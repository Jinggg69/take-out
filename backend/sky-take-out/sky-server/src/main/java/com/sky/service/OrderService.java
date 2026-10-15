package com.sky.service;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;

public interface OrderService {

    /**
     * 用户端订单提交
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付，生成预支付交易单。
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功后修改订单状态。
     * @param outTradeNo 商户订单号
     */
    void paySuccess(String outTradeNo);
}
