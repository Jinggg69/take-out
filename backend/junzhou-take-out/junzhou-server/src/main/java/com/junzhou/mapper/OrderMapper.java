package com.junzhou.mapper;

import com.github.pagehelper.Page;
import com.junzhou.dto.OrdersPageQueryDTO;
import com.junzhou.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {
    @org.apache.ibatis.annotations.Select("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/>select t.name from tenant t join store s on s.tenant_id=t.id where t.id=#{tenant} and s.id=#{store} and t.status=1 and s.status=1</script>")
    String currentMerchantName();
    int dineComplete(Long id);
    int pickupReady(Long id);
    int pickupVerify(@Param("id") Long id,@Param("credential") String credential,@Param("employeeId") Long employeeId,@Param("now") LocalDateTime now);
    @org.apache.ibatis.annotations.Select("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/>select name from store_dining_table where id=#{id} and tenant_id=#{tenant} and store_id=#{store} and enabled=1 for update</script>")
    String lockDiningTable(Long id);
    Orders getBySubmission(@Param("userId") Long userId,@Param("requestId") String requestId);

    List<Orders> cancelledPaymentsToCheck(LocalDateTime before);

    void recordPaymentCheck(@Param("id") Long id,@Param("now") LocalDateTime now);

    int claimPaymentCheck(@Param("id") Long id, @Param("now") LocalDateTime now,
                          @Param("before") LocalDateTime before);

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
    Orders getByNumber(String orderNumber);

    /**
     * 根据订单号查询订单并加行锁，用于处理支付通知
     * @param orderNumber 商户订单号
     * @return 订单信息
     */
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
    Orders getById(Long id);

    /**
     * 根据id查询订单并加行锁，避免并发状态操作和重复退款
     * @param id 订单id
     * @return 订单信息
     */
    Orders getByIdForUpdate(Long id);

    /**
     * 根据订单状态统计订单数量
     * @param status 订单状态
     * @return 订单数量
     */
    Integer countByStatus(Integer status);

    /**
     * 将未支付的待付款订单更新为已支付、待接单状态
     * @param number 商户订单号
     * @return 实际更新的记录数
     */
    int markPaid(String number);

    /**
     * 查询指定状态且下单时间早于指定时间的订单
     * @param status 订单状态
     * @param orderTime 下单时间上限
     * @return 订单列表
     */
    List<Orders> getByStausAndOrderTimeLT(Integer status, LocalDateTime orderTime);
}
