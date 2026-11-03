package com.junzhou.mapper;

import com.junzhou.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList 订单明细数据列表
     */
    int insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     * @param orderId 订单id
     * @return 订单明细列表
     */
    List<OrderDetail> getByOrderId(Long orderId);
}
