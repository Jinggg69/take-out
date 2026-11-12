package com.junzhou.payment;

import com.alibaba.fastjson.JSONObject;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.Orders;
import com.junzhou.entity.User;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.exception.WechatApiException;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.mapper.UserMapper;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.utils.WeChatPayUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.*;

/** Persistent simulator. Every access first resolves an order through the scoped business mapper. */
@Service
public class MockPaymentGateway {
    private final PaymentChannelSettings settings;
    private final OrderMapper orders;
    private final UserMapper users;
    private final JdbcTemplate jdbc;
    public MockPaymentGateway(PaymentChannelSettings settings, OrderMapper orders, UserMapper users, DataSource source) {
        this.settings=settings; this.orders=orders; this.users=users; this.jdbc=new JdbcTemplate(source);
    }
    public boolean enabled() { return settings.mockEnabled(); }
    public String newOrderChannel() { return settings.newOrderChannel(); }
    public static WeChatProperties identity() {
        WeChatProperties p=new WeChatProperties(); p.setAppid("MOCK_APP"); p.setMchid("MOCK_MERCHANT"); return p;
    }
    public static WeChatPayUtil provider(MockPaymentGateway gateway,Long id) {
        return new WeChatPayUtil() {
            @Override public JSONObject pay(String number,BigDecimal amount,String description,String openid) {
                Orders order=gateway.requireOrder(id,false);
                if(!order.getNumber().equals(number) || order.getAmount().compareTo(amount)!=0) throw new SecurityException("Mock order mismatch");
                return gateway.prepare(id);
            }
            @Override public JSONObject queryOrder(String number) { gateway.requireNumber(id,number); return gateway.query(id); }
            @Override public void closeOrder(String number) { gateway.requireNumber(id,number); gateway.close(id); }
            @Override public String refund(String number,String refundNo,BigDecimal amount,BigDecimal total) {
                gateway.requireNumber(id,number); return gateway.refundPayment(id,refundNo,amount,total).toJSONString();
            }
            @Override public String queryRefund(String refundNo) { return gateway.queryRefundPayment(id,refundNo).toJSONString(); }
        };
    }
    private void requireNumber(Long id,String number) {
        if(!requireOrder(id,false).getNumber().equals(number)) throw new SecurityException("Mock order mismatch");
    }
    public Orders requireOrder(Long id,boolean lock) {
        settings.requireMock();
        Orders order=lock ? orders.getByIdForUpdate(id) : orders.getById(id);
        if(order==null || !"MOCK".equals(order.getPaymentChannel())) throw new SecurityException("Not an authorized mock order");
        return order;
    }
    private Map<String,Object> record(Long id) {
        List<Map<String,Object>> rows=jdbc.queryForList("select * from mock_payment where order_id=?",id);
        return rows.isEmpty() ? null : rows.get(0);
    }
    @Transactional
    public JSONObject prepare(Long id) {
        Orders order=requireOrder(id,true);
        if(!Orders.PENDING_PAYMENT.equals(order.getStatus()) || !Orders.UN_PAID.equals(order.getPayStatus()))
            throw new OrderBusinessException("订单当前不能发起模拟支付");
        ensureRecord(order);
        JSONObject response=new JSONObject(); response.put("channel","MOCK"); response.put("orderId",id);
        return response;
    }
    private void ensureRecord(Orders order) {
        if(record(order.getId())!=null) return;
        User user=users.getPaymentUserByOrderId(order.getId());
        if(user==null || user.getOpenid()==null || user.getOpenid().trim().isEmpty()) throw new OrderBusinessException("请先登录后模拟支付");
        jdbc.update("insert into mock_payment(order_id,state,amount_fen,payer_openid) values(?,'NOTPAY',?,?)",
                order.getId(),order.getAmount().movePointRight(2).longValueExact(),user.getOpenid());
    }
    /** Commit the simulated channel result before invoking the normal business callback. */
    @Transactional
    public void choose(Long id,String outcome) {
        RequestScope.requireUserId();
        if(!Arrays.asList("SUCCESS","FAILED","CANCELLED","LOST_NOTIFY").contains(outcome)) throw new OrderBusinessException("未知模拟支付结果");
        Orders order=requireOrder(id,true);
        Map<String,Object> row=record(id);
        if(row!=null && "SUCCESS".equals(row.get("state"))) return;
        if(!Orders.PENDING_PAYMENT.equals(order.getStatus()) || !Orders.UN_PAID.equals(order.getPayStatus()))
            throw new OrderBusinessException("订单已取消或已支付，请查看订单");
        ensureRecord(order); row=record(id);
        if(!"NOTPAY".equals(row.get("state"))) throw new OrderBusinessException("模拟交易已关闭");
        if("SUCCESS".equals(outcome) || "LOST_NOTIFY".equals(outcome)) {
            jdbc.update("update mock_payment set state='SUCCESS',paid_at=? where order_id=? and state='NOTPAY'",
                    Timestamp.from(java.time.Instant.now()),id);
        }
    }
    @Transactional(readOnly=true)
    public JSONObject query(Long id) {
        Orders order=requireOrder(id,false); Map<String,Object> row=record(id);
        JSONObject result=new JSONObject(); result.put("out_trade_no",order.getNumber());
        String state=row==null ? "NOTPAY" : (String)row.get("state");
        result.put("trade_state",state);
        if("SUCCESS".equals(state) || "REFUND".equals(state)) {
            result.put("appid",identity().getAppid()); result.put("mchid",identity().getMchid());
            result.put("trade_type","JSAPI"); result.put("transaction_id","MOCK_"+order.getNumber());
            JSONObject amount=new JSONObject(); amount.put("total",row.get("amount_fen")); amount.put("currency","CNY"); result.put("amount",amount);
            JSONObject payer=new JSONObject(); payer.put("openid",row.get("payer_openid")); result.put("payer",payer);
            result.put("success_time",((Timestamp)row.get("paid_at")).toInstant().atOffset(ZoneOffset.ofHours(8)).toString());
        }
        return result;
    }
    @Transactional public void close(Long id) {
        Orders order=requireOrder(id,true);
        if(!Orders.CANCELLED.equals(order.getStatus())) throw new OrderBusinessException("只能关闭已取消订单的模拟交易");
        if(record(id)==null) ensureRecord(order);
        jdbc.update("update mock_payment set state='CLOSED' where order_id=? and state='NOTPAY'",id);
    }
    @Transactional public JSONObject refundPayment(Long id,String refundNo,BigDecimal amount,BigDecimal total) {
        Orders order=requireOrder(id,true); Map<String,Object> row=record(id);
        if(row==null || !Arrays.asList("SUCCESS","REFUND").contains(row.get("state"))) throw new OrderBusinessException("模拟交易尚未付款");
        if(!Orders.CANCELLED.equals(order.getStatus()) || order.getAmount().compareTo(amount)!=0 || amount.compareTo(total)!=0
                || !order.getNumber().equals(refundNo)) throw new OrderBusinessException("模拟退款金额或身份不匹配");
        jdbc.update("update mock_payment set state='REFUND',out_refund_no=? where order_id=?",refundNo,id);
        return refundResult(order,refundNo,row);
    }
    @Transactional(readOnly=true) public JSONObject queryRefundPayment(Long id,String refundNo) {
        Orders order=requireOrder(id,false); Map<String,Object> row=record(id);
        if(row==null || !refundNo.equals(row.get("out_refund_no"))) throw new WechatApiException(404,"RESOURCE_NOT_EXISTS");
        return refundResult(order,refundNo,row);
    }
    private JSONObject refundResult(Orders order,String number,Map<String,Object> row) {
        JSONObject result=new JSONObject(); result.put("out_trade_no",order.getNumber()); result.put("out_refund_no",number);
        result.put("transaction_id","MOCK_"+order.getNumber()); result.put("refund_id","MOCK_REFUND_"+number); result.put("status","SUCCESS");
        JSONObject amount=new JSONObject(); amount.put("total",row.get("amount_fen")); amount.put("refund",row.get("amount_fen")); amount.put("currency","CNY"); result.put("amount",amount);
        return result;
    }
}
