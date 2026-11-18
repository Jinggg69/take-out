package com.junzhou.service;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.Orders;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.membership.MembershipCheckoutService;
import com.junzhou.payment.OrderPaymentAmounts;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.websocket.WebSocketServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
@Service
public class WalletOrderPaymentService {
 private final OrderMapper orders; private final MembershipCheckoutService members; private final WebSocketServer sockets;
 public WalletOrderPaymentService(OrderMapper orders,MembershipCheckoutService members,WebSocketServer sockets){this.orders=orders;this.members=members;this.sockets=sockets;}
 @Transactional public void pay(Long id){
  Long user=RequestScope.requireUserId(); Orders order=orders.getByIdForUpdate(id);
  if(order==null || !user.equals(order.getUserId()) || OrderPaymentAmounts.external(order).signum()!=0) throw new OrderBusinessException("订单无法使用余额结算");
  if(Orders.PAID.equals(order.getPayStatus())) return;
  if(!Orders.PENDING_PAYMENT.equals(order.getStatus()) || !Orders.UN_PAID.equals(order.getPayStatus())) throw new OrderBusinessException("订单状态已变化");
  members.paid(order);
  orders.update(Orders.builder().id(id).status(Orders.TO_BE_CONFIRMED).payStatus(Orders.PAID).checkoutTime(LocalDateTime.now()).wechatTradeState("INTERNAL_PAID").build());
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){
   JSONObject message=new JSONObject();message.put("type",1);message.put("orderId",id);message.put("content","订单号:"+order.getNumber());
   try{sockets.sendToStore(order.getTenantId(),order.getStoreId(),message.toJSONString());}catch(RuntimeException ignored){}
  }});
 }
}
