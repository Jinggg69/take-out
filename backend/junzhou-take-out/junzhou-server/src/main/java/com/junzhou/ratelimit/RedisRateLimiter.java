package com.junzhou.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class RedisRateLimiter {
    private static final DefaultRedisScript<List> ACQUIRE = script("redis/rate-limit-acquire.lua");
    private static final DefaultRedisScript<List> CHECK_FAILURES = script("redis/login-failure-check.lua");
    private static final DefaultRedisScript<List> RECORD_FAILURE = script("redis/login-failure-record.lua");

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimitDecision acquire(String key, int limit, long windowMillis) {
        return execute(ACQUIRE, key, String.valueOf(limit), String.valueOf(windowMillis));
    }

    public RateLimitDecision checkFailures(String key, int limit) {
        return execute(CHECK_FAILURES, key, String.valueOf(limit));
    }

    public RateLimitDecision recordFailure(String key, int limit, long windowMillis) {
        return execute(RECORD_FAILURE, key, String.valueOf(limit), String.valueOf(windowMillis));
    }

    private RateLimitDecision execute(DefaultRedisScript<List> script, String key, String... args) {
        try {
            List<?> result = redis.execute(script, Collections.singletonList(key), (Object[]) args);
            if (result == null || result.size() != 2 || !(result.get(0) instanceof Number)
                    || !(result.get(1) instanceof Number)) {
                throw new RateLimitUnavailableException();
            }
            long allowed = ((Number) result.get(0)).longValue();
            long ttl = ((Number) result.get(1)).longValue();
            if ((allowed != 0L && allowed != 1L) || ttl < 0L) {
                throw new RateLimitUnavailableException();
            }
            return new RateLimitDecision(allowed == 1L, ttl);
        } catch (RateLimitUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RateLimitUnavailableException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }
}
