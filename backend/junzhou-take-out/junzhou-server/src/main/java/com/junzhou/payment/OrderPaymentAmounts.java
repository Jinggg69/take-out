package com.junzhou.payment;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.entity.Orders;
import com.junzhou.exception.OrderBusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** External provider money is distinct from the total payable on wallet orders. */
public final class OrderPaymentAmounts {
 private OrderPaymentAmounts() {}
 public static BigDecimal external(Orders order) {
  if(order==null || order.getAmount()==null) throw invalid();
  BigDecimal total=money(order.getAmount());
  if(order.getPricingSnapshot()==null || order.getPricingSnapshot().trim().isEmpty()) return total;
  try {
   JSONObject root=JSON.parseObject(order.getPricingSnapshot());
   JSONObject member=root==null?null:root.getJSONObject("member");
   if(member==null) return total;
   BigDecimal external=money(member.getBigDecimal("externalAmount"));
   BigDecimal balance=money(member.getBigDecimal("balanceAmount"));
   BigDecimal principal=money(member.getBigDecimal("principalAmount"));
   BigDecimal gift=money(member.getBigDecimal("giftAmount"));
   if(external.add(balance).compareTo(total)!=0 || principal.add(gift).compareTo(balance)!=0) throw invalid();
   return external;
  } catch(RuntimeException error) { throw invalid(); }
 }
 private static BigDecimal money(BigDecimal amount) {
  if(amount==null || amount.signum()<0) throw invalid();
  try { return amount.setScale(2,RoundingMode.UNNECESSARY); }
  catch(ArithmeticException error) { throw invalid(); }
 }
 private static OrderBusinessException invalid(){return new OrderBusinessException("订单支付金额分摊异常，请联系商家核验");}
}
