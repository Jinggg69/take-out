package com.junzhou.ratelimit;

/** Immutable result of an atomic Redis quota decision. */
public final class RateLimitDecision {
    private final boolean allowed;
    private final long retryAfterMillis;

    public RateLimitDecision(boolean allowed, long retryAfterMillis) {
        this.allowed = allowed;
        this.retryAfterMillis = retryAfterMillis;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
