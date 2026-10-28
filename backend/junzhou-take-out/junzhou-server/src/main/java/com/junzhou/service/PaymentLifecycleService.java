package com.junzhou.service;

import com.alibaba.fastjson.JSONObject;

/** 支付状态业务接口。 */
public interface PaymentLifecycleService {
    /**
     * 校验并确认支付结果，仅可处理已验签通知或可信查询结果。
     * @param payment 微信支付结果
     */
    void confirmPayment(JSONObject payment);

}
