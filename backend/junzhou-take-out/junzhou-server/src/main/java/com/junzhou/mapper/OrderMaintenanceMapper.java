package com.junzhou.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Atomic state transitions used by scheduled order maintenance. */
@Mapper
public interface OrderMaintenanceMapper {

    @Update("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/><bind name='user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>update orders set status = 6, cancel_reason = '订单超时未支付，系统自动取消', cancel_time = #{now} "
            + "where status = 1 and pay_status = 0 and order_time &lt; #{cutoff} and tenant_id=#{tenant} and store_id=#{store} <if test='user != null'>and user_id=#{user}</if> and exists(select 1 from store s where s.id=orders.store_id and s.tenant_id=orders.tenant_id)</script>")
    int cancelUnpaidBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Update("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/><bind name='user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>update orders set status = 5, delivery_time = #{now} "
            + "where status = 4 and pay_status = 1 and dispatch_time is not null and dispatch_time &lt; #{cutoff} and tenant_id=#{tenant} and store_id=#{store} <if test='user != null'>and user_id=#{user}</if> and exists(select 1 from store s where s.id=orders.store_id and s.tenant_id=orders.tenant_id)</script>")
    int completeDeliveriesBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
