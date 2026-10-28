package com.junzhou.service;

/**
 * 配送范围业务接口
 */
public interface DeliveryRangeService {

    /**
     * 校验收货地址是否在门店5公里配送范围内
     * @param deliveryAddress 完整收货地址
     */
    void validate(String deliveryAddress);
}
