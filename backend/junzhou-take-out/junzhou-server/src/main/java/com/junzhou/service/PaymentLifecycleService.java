package com.junzhou.service;

import com.alibaba.fastjson.JSONObject;
import com.junzhou.entity.Orders;
import com.junzhou.entity.User;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.mapper.UserMapper;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@Slf4j
public class PaymentLifecycleService {
    @Autowired private OrderMapper orders;
    @Autowired private UserMapper users;
    @Autowired private WeChatProperties properties;
    @Autowired private RefundTransactionService refunds;
    @Autowired private WebSocketServer sockets;

    /** Only call after a provider signature was verified, including authenticated query responses. */
    @Transactional
    public void confirmPayment(JSONObject payment) {
        if(blank(properties.getMchid()) || blank(properties.getAppid())) throw new IllegalStateException("Payment merchant is not configured");
        if(payment==null || !properties.getMchid().equals(payment.getString("mchid"))
                || !properties.getAppid().equals(payment.getString("appid")) || !"SUCCESS".equals(payment.getString("trade_state"))
                || !"JSAPI".equals(payment.getString("trade_type"))) throw new OrderBusinessException("支付交易信息不匹配");
        String number=payment.getString("out_trade_no"),transaction=payment.getString("transaction_id");
        if(blank(number)||blank(transaction)||transaction.length()>64) throw new OrderBusinessException("支付交易信息缺失");
        Orders order=orders.getByNumberForUpdate(number);
        if(order==null) throw new OrderBusinessException("订单不存在");
        JSONObject amount=payment.getJSONObject("amount"),payer=payment.getJSONObject("payer");
        User user=users.getById(order.getUserId());
        long expected;
        try { expected=order.getAmount().movePointRight(2).longValueExact(); }
        catch(RuntimeException ex) { throw new OrderBusinessException("订单金额异常"); }
        if(amount==null || !"CNY".equals(amount.getString("currency")) || RefundTransactionService.fen(amount.get("total"))!=expected
                || payer==null || user==null || blank(user.getOpenid()) || !user.getOpenid().equals(payer.getString("openid"))) {
            throw new OrderBusinessException("支付金额或付款人信息不匹配");
        }
        if(order.getWechatTransactionId()!=null && !transaction.equals(order.getWechatTransactionId())) throw new OrderBusinessException("支付交易号不匹配");
        LocalDateTime paidAt;
        try { paidAt=OffsetDateTime.parse(payment.getString("success_time")).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(); }
        catch(RuntimeException ex) { throw new OrderBusinessException("支付时间信息异常"); }
        boolean newlyPaid=Orders.PENDING_PAYMENT.equals(order.getStatus()) && Orders.UN_PAID.equals(order.getPayStatus());
        Orders update=Orders.builder().id(order.getId()).wechatTransactionId(transaction).wechatTradeState("SUCCESS")
                .paymentCheckTime(LocalDateTime.now()).build();
        if(order.getCheckoutTime()==null) update.setCheckoutTime(paidAt);
        if(newlyPaid) { update.setStatus(Orders.TO_BE_CONFIRMED); update.setPayStatus(Orders.PAID); }
        orders.update(update);
        order.setWechatTransactionId(transaction);
        if(Orders.CANCELLED.equals(order.getStatus())) {
            // A late payment records the money and creates one durable refund intent without reopening fulfilment.
            if(Orders.UN_PAID.equals(order.getPayStatus())) order.setPayStatus(Orders.PAID);
            refunds.request(order);
        }
        if(newlyPaid) {
            final JSONObject message=new JSONObject(); message.put("type",1); message.put("orderId",order.getId()); message.put("content","订单号:"+number);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try { sockets.sendToAllClient(message.toJSONString()); }
                    catch(RuntimeException ex) { log.warn("订单支付提醒发送失败，orderId={}",order.getId()); }
                }
            });
        }
    }

    private boolean blank(String value) { return value==null||value.trim().isEmpty(); }
}
