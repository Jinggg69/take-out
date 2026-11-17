package com.junzhou.test.support;
import com.junzhou.ratelimit.*;
import org.springframework.context.annotation.*;
@org.springframework.boot.test.context.TestConfiguration
public class DisabledRateLimitConfiguration {
    public static RateLimitGuard guard() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(false);
        return new RateLimitGuard(org.mockito.Mockito.mock(RedisRateLimiter.class), properties);
    }
    @Bean public RateLimitGuard rateLimitGuard() { return guard(); }
}
