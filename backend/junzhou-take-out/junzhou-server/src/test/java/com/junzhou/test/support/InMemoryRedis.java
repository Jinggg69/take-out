package com.junzhou.test.support;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mocked {@link RedisTemplate} backed by an in-memory map with real NX semantics and access
 * counters, so cache hits and misses can be asserted without depending on real TTL expiry.
 */
public final class InMemoryRedis {
    public final Map<String, Object> values = new LinkedHashMap<>();
    public final AtomicInteger reads = new AtomicInteger();
    public final AtomicInteger writes = new AtomicInteger();
    /** TTL of the most recent NX write, so tests can assert the selected base TTL. */
    public final java.util.concurrent.atomic.AtomicLong lastTtlSeconds = new java.util.concurrent.atomic.AtomicLong(-1);
    public final RedisTemplate<String, Object> template;

    @SuppressWarnings("unchecked")
    public InMemoryRedis() {
        template = mock(RedisTemplate.class);
        ValueOperations<String, Object> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(call -> {
            reads.incrementAndGet();
            return values.get((String) call.getArgument(0));
        });
        when(ops.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
            String key = call.getArgument(0);
            if (values.containsKey(key)) return false;
            writes.incrementAndGet();
            lastTtlSeconds.set(call.getArgument(2));
            values.put(key, call.getArgument(1));
            return true;
        });
    }

    public int size() {
        return values.size();
    }

    public String onlyKey() {
        return values.keySet().iterator().next();
    }
}
