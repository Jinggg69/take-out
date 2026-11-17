package com.junzhou.ratelimit;

import com.junzhou.exception.BaseException;

/** The shared protection store failed; protected work must not continue. */
public class RateLimitUnavailableException extends BaseException {
    public static final String PUBLIC_MESSAGE = "操作保护服务暂不可用，请稍后重试";

    public RateLimitUnavailableException() {
        super(PUBLIC_MESSAGE);
    }

    public RateLimitUnavailableException(Throwable cause) {
        super(PUBLIC_MESSAGE);
        initCause(cause);
    }
}
