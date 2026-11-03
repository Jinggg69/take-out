package com.junzhou.task;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.service.PaymentRecoveryService;
import com.junzhou.service.TenantScopeService;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.entity.Orders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;
@Component @Slf4j
public class PaymentRecoveryTask {
    @Autowired private TenantScopeService scopes;
    @Autowired private OrderMaintenanceMapper candidates;
    @Autowired private PaymentRecoveryService refunds;
    @Autowired private WeChatProperties properties;
    @Value("${sky.payment.jobs-enabled:true}") private boolean enabled;
    @Scheduled(cron="15 * * * * ?") public void recover() {
        BackgroundJobScope.clear();
        try {
            if(!enabled || properties.getMchid()==null || properties.getMchid().trim().isEmpty()) return;
            // Stage 2.6 must route payment accounts before this guard can be removed.
            scopes.requireLegacySingleStore();
            try { process(true); } catch(RuntimeException ex) { log.warn("退款恢复任务暂不可用"); }
            try { process(false); } catch(RuntimeException ex) { log.warn("支付核验任务暂不可用"); }
        } finally { BackgroundJobScope.clear(); }
    }
    private void process(boolean refund) {
        long after=0;
        LocalDateTime now=LocalDateTime.now();
        while(true) {
            final long cursor=after;
            List<Orders> batch=BackgroundJobScope.scan(() -> refund ? candidates.refundCandidates(cursor,now)
                    : candidates.cancelledCandidates(cursor,now.minusMinutes(5)));
            if(batch.isEmpty()) return;
            for(Orders order:batch) {
                after=order.getId();
                try { BackgroundJobScope.forOrder(order, () -> {
                    scopes.requireLegacySingleStore();
                    if(!Long.valueOf(1).equals(order.getTenantId()) || !Long.valueOf(1).equals(order.getStoreId()))
                        throw new SecurityException("Payment account routing is not available");
                    if(refund) refunds.process(order.getNumber(),false);
                    else refunds.reconcileOrder(order.getId());
                }); } catch(RuntimeException ex) { log.warn("支付恢复失败，orderId={}",order.getId()); }
            }
        }
    }
}
