package com.junzhou.ratelimit;

import com.junzhou.exception.BaseException;

/** A request was rejected by a configured rate-limit rule. */
public class RateLimitExceededException extends BaseException {
    private final String ruleId;
    private final long retryAfterMillis;

    public RateLimitExceededException(String ruleId, long retryAfterMillis) {
        this(ruleId, retryAfterMillis,
                "操作太频繁，请" + Math.max(1L, (retryAfterMillis + 999L) / 1000L) + "秒后再试");
    }

    public RateLimitExceededException(String ruleId, long retryAfterMillis, String message) {
        super(message);
        this.ruleId = ruleId;
        this.retryAfterMillis = Math.max(0L, retryAfterMillis);
    }

    public String getRuleId() {
        return ruleId;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
