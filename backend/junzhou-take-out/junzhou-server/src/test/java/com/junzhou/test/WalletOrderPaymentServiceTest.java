package com.junzhou.test;
import com.junzhou.service.WalletOrderPaymentService;
import com.junzhou.membership.MembershipCheckoutService;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.entity.Orders;
import com.junzhou.context.RequestScope;
import com.junzhou.websocket.WebSocketServer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class WalletOrderPaymentServiceTest {
 @Test void zeroExternalOrderIsServerConfirmedOnceWithoutProviderEvenOutsideMock(){
  OrderMapper orders=mock(OrderMapper.class);MembershipCheckoutService members=mock(MembershipCheckoutService.class);
  Orders order=Orders.builder().id(9L).userId(5L).tenantId(1L).storeId(10L).number("free-order").paymentChannel("WECHAT").status(Orders.PENDING_PAYMENT).payStatus(Orders.UN_PAID).amount(new BigDecimal("0.00")).build();
  when(orders.getByIdForUpdate(9L)).thenReturn(order);
  doAnswer(call->{Orders changed=call.getArgument(0);order.setStatus(changed.getStatus());order.setPayStatus(changed.getPayStatus());return null;}).when(orders).update(any());
  RequestScope.set(new RequestScope(null,5L,1L,10L,Collections.emptySet()));TransactionSynchronizationManager.initSynchronization();
  try { WalletOrderPaymentService service=new WalletOrderPaymentService(orders,members,mock(WebSocketServer.class));service.pay(9L);service.pay(9L);verify(members,times(1)).paid(order);assertEquals(Orders.PAID,order.getPayStatus());assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size()); }
  finally {TransactionSynchronizationManager.clearSynchronization();RequestScope.clear();}
 }
}
