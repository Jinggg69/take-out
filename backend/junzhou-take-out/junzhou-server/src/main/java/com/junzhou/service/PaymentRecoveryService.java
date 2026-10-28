package com.junzhou.service;



/** 支付恢复业务接口。 */
public interface PaymentRecoveryService {
    /**
     * 处理到期的退款请求。
     */
    void processDue();

    /**
     * 重试指定订单的退款。
     * @param orderId 订单ID
     */
    void retry(Long orderId);

    /**
     * 处理指定退款请求。
     * @param number 退款单号
     * @param force 是否强制重试
     */
    void process(String number, boolean force);

    /**
     * 核验已取消订单的支付状态。
     */
    void reconcileCancelled();

}
