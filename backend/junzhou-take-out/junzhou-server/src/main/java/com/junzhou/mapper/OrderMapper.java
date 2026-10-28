package com.junzhou.mapper;

import com.github.pagehelper.Page;
import com.junzhou.dto.OrdersPageQueryDTO;
import com.junzhou.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    @Select("select * from orders where user_id=#{userId} and client_request_id=#{requestId}")
    Orders getBySubmission(@Param("userId") Long userId,@Param("requestId") String requestId);

    @Select("select * from orders where status=6 and pay_status=0 "
            + "and (wechat_trade_state is null or wechat_trade_state not in ('CLOSED','REVOKED','PAYERROR')) "
            + "and (payment_check_time is null or payment_check_time<#{before}) "
            + "order by coalesce(payment_check_time,order_time),id limit 20")
    List<Orders> cancelledPaymentsToCheck(LocalDateTime before);

    @Update("update orders set payment_check_time=#{now} where id=#{id}")
    void recordPaymentCheck(@Param("id") Long id,@Param("now") LocalDateTime now);

    @Update("update orders set wechat_trade_state=#{state},payment_check_time=#{now} where id=#{id} and status=6 and pay_status=0")
    int markClosedPayment(@Param("id") Long id,@Param("state") String state,@Param("now") LocalDateTime now);

    /**
     * 插入订单数据
     * @param order 订单信息
     */
    void insert(Orders order);

    /**
     * 根据订单号查询订单
     * @param orderNumber 商户订单号
     * @return 订单信息
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 根据订单号查询订单并加行锁，用于处理支付通知
     * @param orderNumber 商户订单号
     * @return 订单信息
     */
    @Select("select * from orders where number = #{orderNumber} for update")
    Orders getByNumberForUpdate(String orderNumber);

    /**
     * 根据id更新订单中非空的状态信息
     * @param orders 待更新的订单信息
     */
    void update(Orders orders);

    /**
     * 根据条件分页查询订单
     * @param ordersPageQueryDTO 订单查询条件
     * @return 订单分页数据
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     * @param id 订单id
     * @return 订单信息
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据id查询订单并加行锁，避免并发状态操作和重复退款
     * @param id 订单id
     * @return 订单信息
     */
    @Select("select * from orders where id = #{id} for update")
    Orders getByIdForUpdate(Long id);

    /**
     * 根据订单状态统计订单数量
     * @param status 订单状态
     * @return 订单数量
     */
    @Select("select count(*) from orders where status = #{status}")
    Integer countByStatus(Integer status);

    /**
     * 将未支付的待付款订单更新为已支付、待接单状态
     * @param number 商户订单号
     * @return 实际更新的记录数
     */
    @Update("update orders set status = 2, pay_status = 1, "
            + "checkout_time = current_timestamp where number = #{number} and status = 1 and pay_status = 0")
    int markPaid(String number);

    /**
     * 查询指定状态且下单时间早于指定时间的订单
     * @param status 订单状态
     * @param orderTime 下单时间上限
     * @return 订单列表
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
    List<Orders> getByStausAndOrderTimeLT(Integer status, LocalDateTime orderTime);
}
