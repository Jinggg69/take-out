package com.sky.task;

import com.sky.properties.WeChatProperties;
import com.sky.service.PaymentReconciliationService;
import com.sky.service.RefundWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentRecoveryTask {
    @Autowired private RefundWorker refunds;
    @Autowired private PaymentReconciliationService payments;
    @Autowired private WeChatProperties properties;
    @Value("${sky.payment.jobs-enabled:true}") private boolean enabled;
    @Scheduled(cron="15 * * * * ?")
    public void recover() {
        if(!enabled || properties.getMchid()==null || properties.getMchid().trim().isEmpty()) return;
        try { refunds.processDue(); } catch(RuntimeException ex) { log.warn("退款恢复任务暂不可用"); }
        try { payments.reconcileCancelled(); } catch(RuntimeException ex) { log.warn("支付核验任务暂不可用"); }
    }
}
