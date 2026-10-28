package com.junzhou.exception;

/**
 * 退款结果待确认异常，保留取消记录并允许使用同一退款单号重试
 */
public class RefundPendingException extends OrderBusinessException {
    /**
     * 创建退款结果待确认异常
     * @param message 退款结果提示信息
     */
    public RefundPendingException(String message) {
        super(message);
    }
}
