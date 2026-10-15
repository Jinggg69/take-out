package com.sky.mapper;

import com.sky.entity.Orders;
import com.sky.dto.OrdersPageQueryDTO;
import com.github.pagehelper.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * @param order 订单数据
     */
    void insert(Orders order);

    /**
     * 根据订单号查询订单。
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    @Select("select * from orders where number = #{orderNumber} for update")
    Orders getByNumberForUpdate(String orderNumber);

    /**
     * 按主键更新订单中非空的状态信息。
     */
    void update(Orders orders);

    Page<Orders> pageQuery(OrdersPageQueryDTO query);

    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    // 状态操作在同一事务中加行锁，避免重复接单、取消与退款。
    @Select("select * from orders where id = #{id} for update")
    Orders getByIdForUpdate(Long id);

    @Select("select count(*) from orders where status = #{status}")
    Integer countByStatus(Integer status);

    @org.apache.ibatis.annotations.Update("update orders set status = 2, pay_status = 1, "
            + "checkout_time = current_timestamp where number = #{number} and status = 1 and pay_status = 0")
    int markPaid(String number);
}
