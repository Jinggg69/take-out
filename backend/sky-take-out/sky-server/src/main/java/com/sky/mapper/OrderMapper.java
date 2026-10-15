package com.sky.mapper;

import com.sky.entity.Orders;
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

    /**
     * 按主键更新订单中非空的状态信息。
     */
    void update(Orders orders);
}
