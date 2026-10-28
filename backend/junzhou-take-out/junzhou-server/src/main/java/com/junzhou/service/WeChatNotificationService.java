package com.junzhou.service;

import com.junzhou.payment.VerifiedNotification;

/** 微信支付通知业务接口。 */
public interface WeChatNotificationService {
    /**
     * 验证并解密微信支付通知。
     * @param rawBody 通知原文
     * @param timestamp 通知时间戳
     * @param nonce 随机串
     * @param serial 平台证书序列号
     * @param signature 通知签名
     * @return 验证后的通知
     */
    VerifiedNotification verify(String rawBody, String timestamp, String nonce, String serial, String signature);

}
