package com.junzhou.test;
import com.junzhou.entity.Orders;
import com.junzhou.payment.OrderPaymentAmounts;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MemberPaymentAmountsTest {
 @Test void legacyOrdersRemainFullyExternal(){
  Orders order=Orders.builder().amount(new BigDecimal("18.50")).build();
  assertEquals(new BigDecimal("18.50"),OrderPaymentAmounts.external(order));
 }
 @Test void mixedOrdersUseImmutableSplitAndRejectUnbalancedSnapshots(){
  Orders order=Orders.builder().amount(new BigDecimal("18.50")).pricingSnapshot("{\"member\":{\"externalAmount\":3.50,\"balanceAmount\":15,\"principalAmount\":12,\"giftAmount\":3}}").build();
  assertEquals(new BigDecimal("3.50"),OrderPaymentAmounts.external(order));
  order.setPricingSnapshot("{\"member\":{\"externalAmount\":3,\"balanceAmount\":15,\"principalAmount\":12,\"giftAmount\":3}}");
  assertThrows(RuntimeException.class,()->OrderPaymentAmounts.external(order));
 }
 @Test void zeroExternalAndMalformedSnapshots(){
  Orders order=Orders.builder().amount(new BigDecimal("10.00")).pricingSnapshot("{\"member\":{\"externalAmount\":0,\"balanceAmount\":10,\"principalAmount\":10,\"giftAmount\":0}}").build();
  assertEquals(new BigDecimal("0.00"),OrderPaymentAmounts.external(order));
  order.setPricingSnapshot("{\"member\":{\"externalAmount\":-1,\"balanceAmount\":11}}");
  assertThrows(RuntimeException.class,()->OrderPaymentAmounts.external(order));
 }
}
