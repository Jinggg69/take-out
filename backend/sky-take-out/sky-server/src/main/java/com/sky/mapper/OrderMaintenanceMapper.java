package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Atomic state transitions used by scheduled order maintenance. */
@Mapper
public interface OrderMaintenanceMapper {

    @Update("update orders set status = 6, cancel_reason = '订单超时未支付，系统自动取消', cancel_time = #{now} "
            + "where status = 1 and pay_status = 0 and order_time < #{cutoff}")
    int cancelUnpaidBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Update("update orders set status = 5, delivery_time = #{now} "
            + "where status = 4 and pay_status = 1 and dispatch_time is not null and dispatch_time < #{cutoff}")
    int completeDeliveriesBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
