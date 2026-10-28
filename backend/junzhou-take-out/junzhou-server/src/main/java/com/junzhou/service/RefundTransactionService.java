package com.junzhou.service;

import com.alibaba.fastjson.JSONObject;
import com.junzhou.entity.Orders;
import com.junzhou.entity.RefundRequest;

/** 退款事务业务接口。 */
public interface RefundTransactionService {
    /**
     * 创建或获取退款请求。
     * @param order 订单
     */
    void request(Orders order);

    /**
     * 领取待处理退款请求。
     * @param number 退款单号
     * @param force 是否强制重试
     * @return 领取的退款请求，无法领取时为空
     */
    RefundRequest claim(String number, boolean force);

    /**
     * 记录退款处理失败并安排重试。
     * @param claim 已领取的退款请求
     * @param code 失败原因代码
     */
    void recordFailure(RefundRequest claim, String code);

    /**
     * 记录微信退款查询或提交结果。
     * @param number 退款单号
     * @param result 微信退款结果
     */
    void recordResult(String number, JSONObject result);

    /**
     * 处理微信退款通知。
     * @param result 微信退款结果
     */
    void notifyResult(JSONObject result);

}
