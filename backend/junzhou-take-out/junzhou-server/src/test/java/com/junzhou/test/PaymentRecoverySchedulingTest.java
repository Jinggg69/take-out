package com.junzhou.test;

import com.junzhou.entity.Orders;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.service.PaymentRecoveryService;
import com.junzhou.task.PaymentRecoveryTask;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRecoverySchedulingTest {
    @Test void providerWorkLeavesSchedulerThreadFreeAndOverlappingTicksAreNotQueued() throws Exception {
        // Register production configuration by name so this test first exposes its absence.
        Class<?> config=Class.forName("com.junzhou.config.PaymentRecoveryConfiguration");
        OrderMaintenanceMapper mapper=mock(OrderMaintenanceMapper.class);
        PaymentRecoveryService service=mock(PaymentRecoveryService.class);
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        when(mapper.refundCandidates(eq(0L),any())).thenAnswer(i -> {
            entered.countDown();
            assertTrue(release.await(5,TimeUnit.SECONDS));
            return Collections.emptyList();
        });
        try(AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext()) {
            context.registerBean(OrderMaintenanceMapper.class,()->mapper);
            context.registerBean(PaymentRecoveryService.class,()->service);
            context.register(config,PaymentRecoveryTask.class);
            context.refresh();
            PaymentRecoveryTask task=context.getBean(PaymentRecoveryTask.class);
            ExecutorService scheduler=Executors.newSingleThreadExecutor();
            try {
                scheduler.submit(task::recover).get(2,TimeUnit.SECONDS);
                assertTrue(entered.await(2,TimeUnit.SECONDS));
                scheduler.submit(task::recover).get(2,TimeUnit.SECONDS);
                org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor=
                    context.getBean("paymentRecoveryExecutor",org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class);
                assertEquals(1,executor.getPoolSize());
                assertEquals(0,executor.getThreadPoolExecutor().getQueue().size());
            } finally { release.countDown(); scheduler.shutdownNow(); }
        }
        verify(mapper,times(1)).refundCandidates(eq(0L),any());
    }

    @Test void pendingWorkIsBoundedAndResumesAtNextPageAcrossTicks() {
        PaymentRecoveryTask task=new PaymentRecoveryTask();
        OrderMaintenanceMapper mapper=mock(OrderMaintenanceMapper.class);
        PaymentRecoveryService service=mock(PaymentRecoveryService.class);
        ReflectionTestUtils.setField(task,"candidates",mapper);
        ReflectionTestUtils.setField(task,"refunds",service);
        ReflectionTestUtils.setField(task,"enabled",true);
        List<Orders> first=new ArrayList<>();
        for(long id=1;id<=100;id++) first.add(Orders.builder().id(id).tenantId(id).storeId(id).build());
        when(mapper.pendingPaymentCandidates(eq(0L),any())).thenReturn(first);
        when(mapper.pendingPaymentCandidates(eq(100L),any())).thenReturn(Collections.singletonList(Orders.builder().id(101L).tenantId(101L).storeId(101L).build()));
        doThrow(new IllegalStateException("One merchant is unavailable")).when(service).reconcileOrder(1L);
        task.recover();
        verify(service,times(100)).reconcileOrder(anyLong());
        verify(service,never()).reconcileOrder(101L);
        task.recover();
        verify(service).reconcileOrder(101L);
        assertNull(com.junzhou.context.RequestScope.current());
    }
}
