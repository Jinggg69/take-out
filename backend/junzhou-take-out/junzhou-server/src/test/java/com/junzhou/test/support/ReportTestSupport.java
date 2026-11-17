package com.junzhou.test.support;

import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.mock;

/** Shared fixtures for report cache tests: a fixed Shanghai clock and an explicitly disabled cache. */
public final class ReportTestSupport {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** Calendar day the workspace fixtures are seeded on. */
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private ReportTestSupport() { }

    public static Clock fixedClock(LocalDateTime shanghaiTime) {
        return Clock.fixed(shanghaiTime.atZone(ZONE).toInstant(), ZONE);
    }

    public static Clock fixedToday() {
        return fixedClock(TODAY.atTime(12, 0));
    }

    public static ReportCacheProperties properties(boolean enabled) {
        ReportCacheProperties properties = new ReportCacheProperties();
        properties.setEnabled(enabled);
        properties.afterPropertiesSet();
        return properties;
    }

    /** Enabled cache that must never be used because no test calls it. */
    public static ReportResultCache disabledCache() {
        return new ReportResultCache(mock(RedisTemplate.class), properties(false));
    }
}
