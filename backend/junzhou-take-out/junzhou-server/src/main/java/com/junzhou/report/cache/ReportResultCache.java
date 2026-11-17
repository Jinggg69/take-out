package com.junzhou.report.cache;

import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cache-aside reader for report display results.
 *
 * <p>Every caller must authorize first and only then call {@link #getOrLoad}. The loader runs
 * outside the cache exception handling so database, authorization and business failures keep
 * propagating exactly as before. Redis problems degrade to a database read, never to a fabricated
 * success.</p>
 */
@Slf4j
@Component
public class ReportResultCache {
    private final RedisTemplate<String, Object> redis;
    private final ReportCacheProperties properties;

    public ReportResultCache(RedisTemplate<String, Object> redis, ReportCacheProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** Deterministic key for an already authorized filter. */
    public String key(ReportCacheKey.View view, ReportFilter filter) {
        return ReportCacheKey.create(properties.getNamespace(), view, filter);
    }

    /** Base TTL for the captured day plus the configured downward jitter. */
    public long ttlSeconds(ReportCacheKey.View view, ReportFilter filter, LocalDate today) {
        return properties.jitteredTtlSeconds(properties.baseTtlSeconds(view, filter, today));
    }

    /**
     * Returns a cached payload or loads it once and stores it with a finite TTL.
     *
     * @param key        key from {@link #key(ReportCacheKey.View, ReportFilter)}
     * @param view       view used to validate the cached structure; must match the key
     * @param ttlSeconds positive time to live applied only on a successful database load
     * @param loader     database loader; invoked for a miss or any Redis read failure
     */
    public Map<String, Object> getOrLoad(String key, ReportCacheKey.View view, long ttlSeconds,
                                         Supplier<Map<String, Object>> loader) {
        if (!properties.isEnabled()) return loader.get();

        final Object cached;
        try {
            cached = redis.opsForValue().get(key);
        } catch (RuntimeException ex) {
            // A duplicate timeout on write would double the request latency; query the database once.
            logFailure("读取", view, ex);
            return loader.get();
        }

        Map<String, Object> hit = valid(view, cached);
        if (hit != null) return hit;

        Map<String, Object> loaded = loader.get();

        try {
            // NX keeps an older concurrent snapshot instead of overwriting it with a later result.
            redis.opsForValue().setIfAbsent(key, new CachedReportResult(loaded),
                    Math.max(1L, ttlSeconds), TimeUnit.SECONDS);
        } catch (RuntimeException ex) {
            logFailure("写入", view, ex);
        }
        return loaded;
    }

    private static void logFailure(String action, ReportCacheKey.View view, RuntimeException ex) {
        // Only the view and the failure type: cached financial values and tokens stay out of logs.
        log.warn("报表缓存{}失败，回查数据库，view={}，原因={}", action, view, ex.getClass().getSimpleName());
    }

    private static Map<String, Object> valid(ReportCacheKey.View view, Object value) {
        if (!(value instanceof CachedReportResult)) return null;
        CachedReportResult result = (CachedReportResult) value;
        if (result.getSchemaVersion() != CachedReportResult.SCHEMA_VERSION) return null;
        Map<String, Object> data = result.getData();
        if (data == null || !matches(view, data)) return null;
        return data;
    }

    /**
     * Minimal structural check for the view's response contract. A malformed value is treated as a
     * miss; the stale key is left to expire instead of being deleted under concurrency.
     */
    private static boolean matches(ReportCacheKey.View view, Map<String, Object> data) {
        switch (view) {
            case WORKSPACE_BUSINESS:
                return decimal(data, "turnover") && decimal(data, "refundAmount") && decimal(data, "netAmount")
                        && decimal(data, "unitPrice") && number(data, "validOrderCount")
                        && number(data, "paidOrderCount") && number(data, "placedOrderCount")
                        && number(data, "cancelledOrderCount") && number(data, "orderCompletionRate")
                        && number(data, "newUsers");
            case WORKSPACE_ORDERS:
                return number(data, "waitingOrders") && number(data, "deliveredOrders")
                        && number(data, "completedOrders") && number(data, "cancelledOrders")
                        && number(data, "allOrders");
            case WORKSPACE_DISHES:
            case WORKSPACE_SETMEALS:
                return number(data, "sold") && number(data, "discontinued");
            case REPORT_TURNOVER:
                return text(data, "dateList") && text(data, "turnoverList")
                        && text(data, "refundList") && text(data, "netList");
            case REPORT_ORDERS:
                return text(data, "dateList") && text(data, "orderCountList")
                        && text(data, "paidOrderCountList") && text(data, "validOrderCountList")
                        && text(data, "cancelledOrderCountList") && number(data, "totalOrderCount")
                        && number(data, "validOrderCount") && number(data, "orderCompletionRate")
                        && number(data, "paidOrderCount") && number(data, "cancelledOrderCount");
            case REPORT_USERS:
                return text(data, "dateList") && text(data, "newUserList") && text(data, "totalUserList");
            case REPORT_TOP10:
                return text(data, "nameList") && text(data, "numberList") && rows(data, "items");
            default:
                return false;
        }
    }

    private static boolean text(Map<String, Object> data, String key) {
        return data.get(key) instanceof String;
    }

    private static boolean decimal(Map<String, Object> data, String key) {
        return data.get(key) instanceof BigDecimal;
    }

    private static boolean number(Map<String, Object> data, String key) {
        return data.get(key) instanceof Number;
    }

    private static boolean rows(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof List)) return false;
        for (Object item : (List<?>) value) {
            if (!(item instanceof ReportRow)) return false;
        }
        return true;
    }
}
