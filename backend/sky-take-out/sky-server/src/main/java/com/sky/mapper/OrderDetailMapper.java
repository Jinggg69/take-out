package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList 订单明细数据列表
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    @org.apache.ibatis.annotations.Select("select * from order_detail where order_id = #{orderId} order by id")
    List<OrderDetail> getByOrderId(Long orderId);
}
