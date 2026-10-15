package com.sky.exception;

/** 退款请求结果不确定时保留取消记录，允许使用同一退款单号重试。 */
public class RefundPendingException extends OrderBusinessException {
    public RefundPendingException(String message) {
        super(message);
    }
}
