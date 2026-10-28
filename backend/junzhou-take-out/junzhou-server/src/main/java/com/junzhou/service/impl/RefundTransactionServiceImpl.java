package com.junzhou.service.impl;

import com.junzhou.service.RefundTransactionService;
import static com.junzhou.payment.PaymentAmounts.fen;

import com.alibaba.fastjson.JSONObject;
import com.junzhou.entity.Orders;
import com.junzhou.entity.RefundRequest;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.mapper.RefundRequestMapper;
import com.junzhou.properties.WeChatProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

/** Short database transactions only. No provider HTTP call is made while these locks are held. */
@Service
public class RefundTransactionServiceImpl implements RefundTransactionService {
    @Autowired private OrderMapper orderMapper;
    @Autowired private RefundRequestMapper refunds;
    @Autowired private WeChatProperties properties;

    /** {@inheritDoc} */
    @Transactional(propagation=Propagation.MANDATORY)
    @Override
    public void request(Orders order) {
        RefundRequest refund=refunds.getByOrderIdForUpdate(order.getId());
        if(refund==null) {
            refund=newRequest(order,order.getNumber(),Orders.REFUND.equals(order.getPayStatus()));
            refunds.insert(refund);
        }
        updateFinancialState(order,refund);
    }

    /** {@inheritDoc} */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @Override
    public RefundRequest claim(String number,boolean force) {
        RefundRequest refund=refunds.getByNumberForUpdate(number);
        LocalDateTime now=LocalDateTime.now();
        if(refund==null || RefundRequest.SUCCESS.equals(refund.getStatus())) return null;
        if(!force && (terminal(refund.getStatus()) || refund.getNextAttemptAt().isAfter(now))) return null;
        if(refund.getLeaseUntil()!=null && refund.getLeaseUntil().isAfter(now)) return null;
        refund.setAttempts(refund.getAttempts()+1);
        refund.setLeaseToken(UUID.randomUUID().toString());
        refund.setLeaseUntil(now.plusMinutes(2));
        refund.setUpdatedAt(now);
        refunds.update(refund);
        return refund;
    }

    /** {@inheritDoc} */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @Override
    public void recordFailure(RefundRequest claim,String code) {
        RefundRequest current=refunds.getByNumberForUpdate(claim.getOutRefundNo());
        if(current==null || !claim.getLeaseToken().equals(current.getLeaseToken()) || terminal(current.getStatus())) return;
        current.setLeaseToken(null); current.setLeaseUntil(null);
        current.setLastError(code!=null && code.matches("[A-Z0-9_]{1,64}") ? code : "RESULT_UNCONFIRMED");
        current.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoff(current.getAttempts())));
        current.setUpdatedAt(LocalDateTime.now());
        refunds.update(current);
    }

    /** {@inheritDoc} */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    @Override
    public void recordResult(String number,JSONObject result) {
        RefundRequest observed=refunds.getByNumber(number);
        if(observed==null) throw new OrderBusinessException("退款请求不存在");
        Orders order=orderMapper.getByIdForUpdate(observed.getOrderId());
        RefundRequest current=refunds.getByNumberForUpdate(number);
        apply(order,current,result,false);
    }

    /** {@inheritDoc} */
    @Transactional
    @Override
    public void notifyResult(JSONObject result) {
        if(blank(properties.getMchid())) throw new IllegalStateException("Payment merchant is not configured");
        if(!properties.getMchid().equals(result.getString("mchid"))) throw new OrderBusinessException("退款商户信息不匹配");
        Orders order=orderMapper.getByNumberForUpdate(result.getString("out_trade_no"));
        if(order==null) throw new OrderBusinessException("订单不存在");
        RefundRequest current=refunds.getByOrderIdForUpdate(order.getId());
        if(current==null) {
            // A verified full-refund notification may arrive before the local request/response or payment callback.
            current=newRequest(order,required(result,"out_refund_no"),true);
            validate(order,current,result,true);
            refunds.insert(current);
        }
        apply(order,current,result,true);
    }

    /** 应用并保存退款结果。 */
    private void apply(Orders order,RefundRequest current,JSONObject result,boolean notification) {
        validate(order,current,result,notification);
        String state=result.getString(notification ? "refund_status" : "status");
        if(!Arrays.asList(RefundRequest.PROCESSING,RefundRequest.SUCCESS,RefundRequest.CLOSED,RefundRequest.ABNORMAL).contains(state)) {
            throw new OrderBusinessException("退款结果尚未确认");
        }
        if(RefundRequest.SUCCESS.equals(current.getStatus())) return;
        if(terminal(current.getStatus()) && RefundRequest.PROCESSING.equals(state)) return;
        current.setStatus(state);
        current.setProviderRefundId(required(result,"refund_id"));
        current.setTransactionId(required(result,"transaction_id"));
        current.setLeaseToken(null); current.setLeaseUntil(null); current.setLastError(null);
        current.setUpdatedAt(LocalDateTime.now());
        current.setNextAttemptAt(LocalDateTime.now().plusSeconds(backoff(current.getAttempts())));
        refunds.update(current);
        if(order.getWechatTransactionId()==null) order.setWechatTransactionId(current.getTransactionId());
        updateFinancialState(order,current);
    }

    /** 校验退款结果与订单信息。 */
    private void validate(Orders order,RefundRequest refund,JSONObject result,boolean notification) {
        if(order==null || refund==null || result==null
                || !refund.getOutRefundNo().equals(result.getString("out_refund_no"))
                || !order.getNumber().equals(result.getString("out_trade_no"))) throw new OrderBusinessException("退款订单信息不匹配");
        String providerId=required(result,"refund_id"),transaction=required(result,"transaction_id");
        if((refund.getProviderRefundId()!=null && !refund.getProviderRefundId().equals(providerId))
                || (order.getWechatTransactionId()!=null && !order.getWechatTransactionId().equals(transaction))
                || (refund.getTransactionId()!=null && !refund.getTransactionId().equals(transaction))) {
            throw new OrderBusinessException("退款交易信息不匹配");
        }
        JSONObject amount=result.getJSONObject("amount");
        // Refund notifications omit currency; authenticated query/submit responses carry it.
        // This application creates only CNY orders. Reject a supplied conflicting currency in either shape.
        if(amount==null || (!notification && !"CNY".equals(amount.getString("currency")))
                || (amount.containsKey("currency") && !"CNY".equals(amount.getString("currency")))
                || fen(amount.get("total"))!=refund.getAmountFen()
                || fen(amount.get("refund"))!=refund.getAmountFen()) throw new OrderBusinessException("退款金额信息不匹配");
        if(notification && !properties.getMchid().equals(result.getString("mchid"))) throw new OrderBusinessException("退款商户信息不匹配");
    }

    /** 构建退款请求。 */
    private RefundRequest newRequest(Orders order,String number,boolean queryOnly) {
        if(blank(number) || number.length()>64 || order.getAmount()==null || order.getAmount().signum()<=0) {
            throw new OrderBusinessException("退款订单信息异常，请联系商家核验");
        }
        long amount;
        try { amount=order.getAmount().movePointRight(2).longValueExact(); }
        catch(ArithmeticException ex) { throw new OrderBusinessException("退款金额异常"); }
        RefundRequest refund=new RefundRequest();
        refund.setOutRefundNo(number); refund.setOrderId(order.getId()); refund.setOutTradeNo(order.getNumber());
        refund.setTransactionId(order.getWechatTransactionId()); refund.setAmountFen(amount);
        refund.setStatus(queryOnly ? RefundRequest.VERIFYING : RefundRequest.REQUESTED); refund.setQueryOnly(queryOnly);
        refund.setAttempts(0); refund.setCreatedAt(LocalDateTime.now()); refund.setUpdatedAt(refund.getCreatedAt());
        refund.setNextAttemptAt(refund.getCreatedAt());
        return refund;
    }

    /** 更新订单退款状态。 */
    private void updateFinancialState(Orders order,RefundRequest refund) {
        Orders update=Orders.builder().id(order.getId()).refundStatus(refund.getStatus())
                .wechatTransactionId(order.getWechatTransactionId()).build();
        if(RefundRequest.SUCCESS.equals(refund.getStatus())) update.setPayStatus(Orders.REFUND);
        else if(RefundRequest.CLOSED.equals(refund.getStatus()) || RefundRequest.ABNORMAL.equals(refund.getStatus())) update.setPayStatus(Orders.REFUND_FAILED);
        else update.setPayStatus(Orders.REFUND_PROCESSING);
        // An external full refund stops any still-unfulfilled order, while retaining completed delivery history.
        if(!Orders.CANCELLED.equals(order.getStatus()) && !Orders.COMPLETED.equals(order.getStatus())) {
            update.setStatus(Orders.CANCELLED); update.setCancelReason("支付退款，订单已取消"); update.setCancelTime(LocalDateTime.now());
        }
        orderMapper.update(update);
    }

    /** 计算退款重试间隔。 */
    private static long backoff(int attempts) { return Math.min(1800,60L << Math.min(Math.max(attempts-1,0),5)); }
    /** 判断退款状态是否为终态。 */
    private boolean terminal(String state) { return RefundRequest.SUCCESS.equals(state)||RefundRequest.CLOSED.equals(state)||RefundRequest.ABNORMAL.equals(state); }
    /** 读取必填的退款字段。 */
    private String required(JSONObject object,String key) {
        String value=object.getString(key);
        if(blank(value)||value.length()>64) throw new OrderBusinessException("退款交易信息缺失");
        return value;
    }
    /** 判断字符串是否为空。 */
    private boolean blank(String value) { return value==null || value.trim().isEmpty(); }
}
