package com.sky.service;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

/**
 * 订单业务接口
 */
public interface OrderService {

    /**
     * 用户下单
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付，生成微信预支付交易单
     * @param ordersPaymentDTO 订单支付信息
     * @return 小程序调起支付所需的参数
     * @throws Exception 微信支付调用异常
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 处理支付成功通知，更新支付状态或为已取消订单申请退款
     * @param outTradeNo 商户订单号
     */
    void paySuccess(String outTradeNo);

    /**
     * 分页查询当前用户的历史订单
     * @param page 页码
     * @param pageSize 每页记录数
     * @param status 订单状态，不传时查询全部状态
     * @return 历史订单分页结果
     */
    PageResult historyOrders(int page, int pageSize, Integer status);

    /**
     * 查询当前用户的订单详情
     * @param id 订单id
     * @return 订单和订单明细
     */
    OrderVO userOrderDetail(Long id);

    /**
     * 用户取消订单，已支付订单同时申请退款
     * @param id 订单id
     */
    void userCancelById(Long id);

    /**
     * 再来一单，将历史订单中的商品加入当前用户购物车
     * @param id 订单id
     */
    void repetition(Long id);

    /**
     * 商家端订单条件分页查询
     * @param ordersPageQueryDTO 订单查询条件
     * @return 订单分页结果
     */
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 统计待接单、待派送和派送中的订单数量
     * @return 各状态订单数量
     */
    OrderStatisticsVO statistics();

    /**
     * 商家查询订单详情
     * @param id 订单id
     * @return 订单和订单明细
     */
    OrderVO details(Long id);

    /**
     * 商家接单
     * @param ordersConfirmDTO 接单信息
     */
    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    /**
     * 商家拒单，已支付订单同时申请退款
     * @param ordersRejectionDTO 拒单信息
     */
    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    /**
     * 商家取消订单，已支付订单同时申请退款
     * @param ordersCancelDTO 订单取消信息
     */
    void cancel(OrdersCancelDTO ordersCancelDTO);

    /**
     * 派送订单
     * @param id 订单id
     */
    void delivery(Long id);

    /**
     * 完成订单，记录实际送达时间
     * @param id 订单id
     */
    void complete(Long id);

    /**
     * 用户催单，向商家发送WebSocket消息
     * @param id 订单id
     */
    void reminder(Long id);

}
