package com.junzhou.task;
import com.junzhou.service.PaymentRecoveryService;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.entity.Orders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
import java.util.EnumMap;
@Component @Slf4j
public class PaymentRecoveryTask {
    private enum Kind { REFUND, CANCELLED, PENDING }
    private final EnumMap<Kind,Long> cursors=new EnumMap<>(Kind.class);
    private static final int MAX_PER_KIND=100;
    private static final long BUDGET_NANOS=java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
    @Autowired private OrderMaintenanceMapper candidates;
    @Autowired private PaymentRecoveryService refunds;
    @Value("${sky.payment.jobs-enabled:true}") private boolean enabled;
    @Async("paymentRecoveryExecutor")
    @Scheduled(cron="15 * * * * ?") public void recover() {
        BackgroundJobScope.clear();
        try {
            if(!enabled) return;
            try { process(Kind.REFUND); } catch(RuntimeException ex) { log.warn("退款恢复任务暂不可用"); }
            try { process(Kind.CANCELLED); } catch(RuntimeException ex) { log.warn("取消订单支付核验任务暂不可用"); }
            try { process(Kind.PENDING); } catch(RuntimeException ex) { log.warn("待付款订单支付核验任务暂不可用"); }
        } finally { BackgroundJobScope.clear(); }
    }
    private void process(Kind kind) {
        long after=cursors.getOrDefault(kind,0L);
        long started=System.nanoTime();
        int handled=0;
        LocalDateTime now=LocalDateTime.now();
        while(handled<MAX_PER_KIND && System.nanoTime()-started<BUDGET_NANOS) {
            final long cursor=after;
            List<Orders> batch=BackgroundJobScope.scan(() -> kind==Kind.REFUND ? candidates.refundCandidates(cursor,now)
                    : kind==Kind.CANCELLED ? candidates.cancelledCandidates(cursor,now.minusMinutes(5))
                    : candidates.pendingPaymentCandidates(cursor,now.minusSeconds(5)));
            if(batch.isEmpty()) { cursors.put(kind,0L); return; }
            for(Orders order:batch) {
                if(handled>=MAX_PER_KIND || System.nanoTime()-started>=BUDGET_NANOS) return;
                after=order.getId();
                cursors.put(kind,after);
                handled++;
                try { BackgroundJobScope.forOrder(order, () -> {
                    if(kind==Kind.REFUND) refunds.process(order.getNumber(),false);
                    else refunds.reconcileOrder(order.getId());
                }); } catch(RuntimeException ex) { log.warn("支付恢复失败，orderId={}",order.getId()); }
            }
        }
    }
}
