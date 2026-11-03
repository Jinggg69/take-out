package com.junzhou.task;

import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {
    @Autowired private com.junzhou.payment.LegacySystemScope systemScope;

    private final OrderMaintenanceMapper orderMaintenanceMapper;
    private final Clock clock;

    @Autowired
    public OrderTask(OrderMaintenanceMapper orderMaintenanceMapper) {
        this(orderMaintenanceMapper, Clock.systemDefaultZone());
    }

    public OrderTask(OrderMaintenanceMapper orderMaintenanceMapper, Clock clock) {
        this.orderMaintenanceMapper = orderMaintenanceMapper;
        this.clock = clock;
    }

    /**
     * 定时处理订单状态
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次
    public void processTimeoutOrders() {
        RequestScope.clear(); BaseContext.removeCurrentId();
        try (com.junzhou.payment.LegacySystemScope.Scope ignored=systemScope.enter()) {
            LocalDateTime now = LocalDateTime.now(clock);
            int cancelled = orderMaintenanceMapper.cancelUnpaidBefore(now.minusMinutes(15), now);
            log.info("定时取消超时未支付订单:{}", cancelled);
        } finally { RequestScope.clear(); BaseContext.removeCurrentId(); }
    }

    /**
     * 定时处理一直处于配送中的订单
     * 每天凌晨一点执行一次
     */
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨一点执行一次
    public void processDeliveryOrders() {
        RequestScope.clear(); BaseContext.removeCurrentId();
        try (com.junzhou.payment.LegacySystemScope.Scope ignored=systemScope.enter()) {
            LocalDateTime now = LocalDateTime.now(clock);
            int completed = orderMaintenanceMapper.completeDeliveriesBefore(now.minusHours(1), now);
            log.info("定时完成配送订单:{}", completed);
        } finally { RequestScope.clear(); BaseContext.removeCurrentId(); }
    }
}
