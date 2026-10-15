package com.sky.service;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersCancelDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderVO;
import com.sky.vo.OrderStatisticsVO;

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

    PageResult historyOrders(int page, int pageSize, Integer status);

    OrderVO userOrderDetail(Long id);

    void userCancelById(Long id);

    void repetition(Long id);

    PageResult conditionSearch(OrdersPageQueryDTO query);

    OrderStatisticsVO statistics();

    OrderVO details(Long id);

    void confirm(OrdersConfirmDTO dto);

    void rejection(OrdersRejectionDTO dto);

    void cancel(OrdersCancelDTO dto);

    void delivery(Long id);

    void complete(Long id);
}
