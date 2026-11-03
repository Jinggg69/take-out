package com.junzhou.task;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.entity.Orders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
@Component @Slf4j
public class OrderTask {
    private final OrderMaintenanceMapper orderMaintenanceMapper;
    private final Clock clock;
    @Autowired public OrderTask(OrderMaintenanceMapper mapper) { this(mapper,Clock.systemDefaultZone()); }
    public OrderTask(OrderMaintenanceMapper mapper, Clock clock) { this.orderMaintenanceMapper=mapper; this.clock=clock; }
    @Scheduled(cron="0 * * * * ?") public void processTimeoutOrders() { process(true); }
    @Scheduled(cron="0 0 1 * * ?") public void processDeliveryOrders() { process(false); }
    private void process(boolean timeout) {
        BackgroundJobScope.clear();
        try {
            LocalDateTime now=LocalDateTime.now(clock), cutoff=timeout ? now.minusMinutes(15) : now.minusHours(1);
            long after=0;
            while (true) {
                final long cursor=after;
                List<Orders> batch=BackgroundJobScope.scan(() -> timeout ? orderMaintenanceMapper.timeoutCandidates(cursor,cutoff)
                        : orderMaintenanceMapper.deliveryCandidates(cursor,cutoff));
                if (batch.isEmpty()) break;
                for (Orders order:batch) {
                    after=order.getId();
                    try { BackgroundJobScope.forOrder(order, () -> {
                        if(timeout) orderMaintenanceMapper.cancelOrderBefore(order.getId(),cutoff,now);
                        else orderMaintenanceMapper.completeOrderBefore(order.getId(),cutoff,now);
                    }); } catch(RuntimeException ex) { log.warn("订单维护失败，orderId={}",order.getId()); }
                }
            }
        } finally { BackgroundJobScope.clear(); }
    }
}
