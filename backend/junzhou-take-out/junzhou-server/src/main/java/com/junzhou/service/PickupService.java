package com.junzhou.service;

/** 自取制作及核销业务；调用入口必须执行独立 RBAC 授权。 */
public interface PickupService {
    /**
     * 将当前门店已支付的制作中自取订单标为待取。
     * @param id 订单 ID
     * @throws com.junzhou.exception.OrderBusinessException 订单不存在或前置状态无效
     */
    void ready(Long id);
    /**
     * 原子核销当前门店待取订单并记录店员。
     * @param id 订单 ID
     * @param credential 顾客提供的随机凭证
     * @throws com.junzhou.exception.OrderBusinessException 凭证、支付或状态无效，或重复核销
     */
    void verify(Long id, String credential);
}
