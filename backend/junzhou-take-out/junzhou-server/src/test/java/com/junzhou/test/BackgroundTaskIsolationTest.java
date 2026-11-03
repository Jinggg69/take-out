package com.junzhou.test;
import com.junzhou.context.*;
import com.junzhou.entity.Orders;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.task.*;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BackgroundTaskIsolationTest {
    @AfterEach void clear() { BackgroundJobScope.clear(); }
    @Test void failedOrderDoesNotLeakScopeOrPreventTheNextTenant() {
        OrderMaintenanceMapper mapper=mock(OrderMaintenanceMapper.class);
        Orders a=Orders.builder().id(1L).tenantId(101L).storeId(1001L).build();
        Orders b=Orders.builder().id(2L).tenantId(102L).storeId(2001L).build();
        when(mapper.timeoutCandidates(eq(0L),any())).thenReturn(Arrays.asList(a,b));
        when(mapper.timeoutCandidates(eq(2L),any())).thenReturn(Collections.emptyList());
        when(mapper.cancelOrderBefore(eq(1L),any(),any())).thenAnswer(i -> {
            assertEquals(101L,RequestScope.requireTenantId());
            BaseContext.setCurrentId(42L);
            throw new IllegalStateException();
        });
        when(mapper.cancelOrderBefore(eq(2L),any(),any())).thenAnswer(i -> {
            assertEquals(102L,RequestScope.requireTenantId()); assertEquals(2001L,RequestScope.requireStoreId());
            assertNull(BaseContext.getCurrentId()); return 1;
        });
        new OrderTask(mapper,Clock.systemUTC()).processTimeoutOrders();
        verify(mapper).cancelOrderBefore(eq(2L),any(),any());
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
        assertThrows(SecurityException.class,BackgroundJobScope::requireScanning);
    }
    @Test void paymentRecoveryCarriesOwnershipAcrossTenantsThroughFailures() {
        PaymentRecoveryTask task=new PaymentRecoveryTask();
        OrderMaintenanceMapper mapper=mock(OrderMaintenanceMapper.class);
        PaymentRecoveryService refunds=mock(PaymentRecoveryService.class);
        WeChatProperties properties=new WeChatProperties(); properties.setMchid("test");
        ReflectionTestUtils.setField(task,"candidates",mapper); ReflectionTestUtils.setField(task,"refunds",refunds);
        ReflectionTestUtils.setField(task,"enabled",true);
        Orders a=Orders.builder().id(1L).tenantId(1L).storeId(1L).number("refund-1").build();
        Orders b=Orders.builder().id(2L).tenantId(2L).storeId(2L).build();
        when(mapper.refundCandidates(eq(0L),any())).thenReturn(Collections.singletonList(a));
        when(mapper.refundCandidates(eq(1L),any())).thenReturn(Collections.emptyList());
        when(mapper.cancelledCandidates(eq(0L),any())).thenReturn(Collections.singletonList(b));
        when(mapper.cancelledCandidates(eq(2L),any())).thenReturn(Collections.emptyList());
        doAnswer(i -> { assertEquals(1L,RequestScope.requireStoreId()); throw new IllegalStateException(); })
                .when(refunds).process("refund-1",false);
        doAnswer(i -> { assertEquals(2L,RequestScope.requireTenantId()); assertEquals(2L,RequestScope.requireStoreId()); assertNull(BaseContext.getCurrentId()); return null; })
                .when(refunds).reconcileOrder(2L);
        task.recover();
        verify(refunds).reconcileOrder(2L); assertNull(RequestScope.current());
        clearInvocations(refunds,mapper);
        ReflectionTestUtils.setField(task,"enabled",false);
        task.recover();
        verifyNoInteractions(refunds,mapper); assertNull(RequestScope.current());
    }
}
