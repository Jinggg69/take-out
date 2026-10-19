package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务类，定时处理订单状态
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 定时处理订单状态
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次
    public void processTimeoutOrders() {
        log.info("定时处理订单状态");
        LocalDateTime time = LocalDateTime.now().minusMinutes(15);
        List<Orders> ordersList = orderMapper.getByStausAndOrderTimeLT(Orders.UN_PAID, time);
        for (Orders order : ordersList) {
            order.setStatus(Orders.CANCELLED);
            order.setCancelReason("订单超时未支付，系统自动取消");
            order.setCancelTime(LocalDateTime.now());
            orderMapper.update(order);
        }
    }

    /**
     * 定时处理一直处于配送中的订单
     * 每天凌晨一点执行一次
     */
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨一点执行一次
    public void processDeliveryOrders() {
        log.info("定时处理配送中的订单:{}", LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().minusHours(1);
        List<Orders> ordersList = orderMapper.getByStausAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        for (Orders order : ordersList) {
            order.setStatus(Orders.COMPLETED);
            orderMapper.update(order);
        }
    }
}
