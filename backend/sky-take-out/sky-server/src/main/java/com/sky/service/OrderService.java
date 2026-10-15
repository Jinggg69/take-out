package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.vo.OrderSubmitVO;

public interface OrderService {

    /**
     * 用户端订单提交
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO);
}
