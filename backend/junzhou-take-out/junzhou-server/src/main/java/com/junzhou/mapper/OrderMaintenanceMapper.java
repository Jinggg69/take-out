package com.junzhou.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** Atomic state transitions used by scheduled order maintenance. */
@Mapper
public interface OrderMaintenanceMapper {

    @org.apache.ibatis.annotations.Select("<script><bind name='job' value='@com.junzhou.task.BackgroundJobScope@requireScanning()'/>select o.id,o.tenant_id,o.store_id,r.out_refund_no as number from refund_request r join orders o on o.id=r.order_id join store s on s.id=o.store_id and s.tenant_id=o.tenant_id join tenant t on t.id=o.tenant_id where o.id &gt; #{after} and r.status in ('REQUESTED','PROCESSING','VERIFYING') and r.next_attempt_at &lt;= #{now} and (r.lease_until is null or r.lease_until &lt;= #{now}) order by o.id limit 100</script>")
    java.util.List<com.junzhou.entity.Orders> refundCandidates(@Param("after") long after,@Param("now") LocalDateTime now);
    @org.apache.ibatis.annotations.Select("<script><bind name='job' value='@com.junzhou.task.BackgroundJobScope@requireScanning()'/>select o.id,o.tenant_id,o.store_id from orders o join store s on s.id=o.store_id and s.tenant_id=o.tenant_id join tenant t on t.id=o.tenant_id where o.id &gt; #{after} and o.status=6 and o.pay_status=0 and (o.wechat_trade_state is null or o.wechat_trade_state not in ('CLOSED','REVOKED','PAYERROR')) and (o.payment_check_time is null or o.payment_check_time &lt; #{before}) order by o.id limit 100</script>")
    java.util.List<com.junzhou.entity.Orders> cancelledCandidates(@Param("after") long after,@Param("before") LocalDateTime before);
    // Candidate scans expose ownership only; each subsequent write rechecks scope and state.
    @org.apache.ibatis.annotations.Select("<script><bind name='job' value='@com.junzhou.task.BackgroundJobScope@requireScanning()'/>select o.id,o.tenant_id,o.store_id from orders o join store s on s.id=o.store_id and s.tenant_id=o.tenant_id join tenant t on t.id=o.tenant_id where o.id &gt; #{after} and o.status=1 and o.pay_status=0 and o.order_time &lt; #{cutoff} order by o.id limit 100</script>")
    java.util.List<com.junzhou.entity.Orders> timeoutCandidates(@Param("after") long after, @Param("cutoff") LocalDateTime cutoff);
    @org.apache.ibatis.annotations.Select("<script><bind name='job' value='@com.junzhou.task.BackgroundJobScope@requireScanning()'/>select o.id,o.tenant_id,o.store_id from orders o join store s on s.id=o.store_id and s.tenant_id=o.tenant_id join tenant t on t.id=o.tenant_id where o.id &gt; #{after} and o.status=4 and o.pay_status=1 and o.dispatch_time &lt; #{cutoff} order by o.id limit 100</script>")
    java.util.List<com.junzhou.entity.Orders> deliveryCandidates(@Param("after") long after, @Param("cutoff") LocalDateTime cutoff);
    @Update("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/><bind name='user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>update orders set status = 6, cancel_reason = '订单超时未支付，系统自动取消', cancel_time = #{now} "
            + "where id = #{id} and status = 1 and pay_status = 0 and order_time &lt; #{cutoff} and tenant_id=#{tenant} and store_id=#{store} <if test='user != null'>and user_id=#{user}</if> and exists(select 1 from store s where s.id=orders.store_id and s.tenant_id=orders.tenant_id)</script>")
    int cancelOrderBefore(@Param("id") Long id, @Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
    @Update("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/><bind name='user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>update orders set status = 5, delivery_time = #{now} "
            + "where id = #{id} and status = 4 and pay_status = 1 and dispatch_time is not null and dispatch_time &lt; #{cutoff} and tenant_id=#{tenant} and store_id=#{store} <if test='user != null'>and user_id=#{user}</if> and exists(select 1 from store s where s.id=orders.store_id and s.tenant_id=orders.tenant_id)</script>")
    int completeOrderBefore(@Param("id") Long id, @Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);


    @Update("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/><bind name='user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>update orders set status = 6, cancel_reason = '订单超时未支付，系统自动取消', cancel_time = #{now} "
            + "where status = 1 and pay_status = 0 and order_time &lt; #{cutoff} and tenant_id=#{tenant} and store_id=#{store} <if test='user != null'>and user_id=#{user}</if> and exists(select 1 from store s where s.id=orders.store_id and s.tenant_id=orders.tenant_id)</script>")
    int cancelUnpaidBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Update("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/><bind name='user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>update orders set status = 5, delivery_time = #{now} "
            + "where status = 4 and pay_status = 1 and dispatch_time is not null and dispatch_time &lt; #{cutoff} and tenant_id=#{tenant} and store_id=#{store} <if test='user != null'>and user_id=#{user}</if> and exists(select 1 from store s where s.id=orders.store_id and s.tenant_id=orders.tenant_id)</script>")
    int completeDeliveriesBefore(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
