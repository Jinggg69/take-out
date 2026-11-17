package com.junzhou.test;

import com.junzhou.exception.OrderBusinessException;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;
import com.junzhou.report.cache.CachedReportResult;
import com.junzhou.report.cache.ReportCacheKey;
import com.junzhou.report.cache.ReportCacheKey.View;
import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.test.support.ReportTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Cache-aside behaviour with a mocked Redis client: hit, miss, NX and every failure path. */
@SuppressWarnings("unchecked")
class ReportResultCacheTest {
    private final Map<String, Object> store = new HashMap<>();
    private RedisTemplate<String, Object> redis;
    private ValueOperations<String, Object> ops;
    private ReportCacheProperties properties;
    private ReportResultCache cache;

    @BeforeEach
    void setUp() {
        redis = mock(RedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(call -> store.get((String) call.getArgument(0)));
        when(ops.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenAnswer(call -> {
            String key = call.getArgument(0);
            if (store.containsKey(key)) return false;
            store.put(key, call.getArgument(1));
            return true;
        });
        properties = ReportTestSupport.properties(true);
        cache = new ReportResultCache(redis, properties);
    }

    private static ReportFilter filter() {
        return new ReportFilter(1L, Arrays.asList(1L, 2L),
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 17, 0, 0));
    }

    private static Map<String, Object> turnoverData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dateList", "2026-11-01,2026-11-02");
        data.put("turnoverList", "0.00,30.00");
        data.put("refundList", "0.00,0.00");
        data.put("netList", "0.00,30.00");
        return data;
    }

    private static Map<String, Object> top10Data() {
        ReportRow row = new ReportRow();
        row.setDishId(9007199254740993L);
        row.setName("fish");
        row.setQuantity(2L);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nameList", "");
        data.put("numberList", "");
        data.put("items", Collections.singletonList(row));
        return data;
    }

    private static Map<String, Object> businessData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("turnover", new BigDecimal("170.00"));
        data.put("refundAmount", new BigDecimal("10.00"));
        data.put("netAmount", new BigDecimal("160.00"));
        data.put("unitPrice", new BigDecimal("42.50"));
        data.put("validOrderCount", 1L);
        data.put("paidOrderCount", 4L);
        data.put("placedOrderCount", 4L);
        data.put("cancelledOrderCount", 1L);
        data.put("orderCompletionRate", 0.25d);
        data.put("newUsers", 4L);
        return data;
    }

    @Test
    void secondCallIsServedFromCacheWithoutLoading() {
        String key = cache.key(View.REPORT_TURNOVER, filter());
        long ttl = cache.ttlSeconds(View.REPORT_TURNOVER, filter(), ReportTestSupport.TODAY);
        assertTrue(ttl >= 118L && ttl <= 120L, "historical ttl " + ttl);
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> first = cache.getOrLoad(key, View.REPORT_TURNOVER, ttl, () -> {
            loads.incrementAndGet();
            return turnoverData();
        });
        Map<String, Object> second = cache.getOrLoad(key, View.REPORT_TURNOVER, ttl, () -> {
            loads.incrementAndGet();
            return turnoverData();
        });

        assertEquals(1, loads.get());
        assertEquals(first, second);
        assertTrue(store.get(key) instanceof CachedReportResult);
        assertEquals(CachedReportResult.SCHEMA_VERSION, ((CachedReportResult) store.get(key)).getSchemaVersion());
        verify(ops, times(1)).setIfAbsent(eq(key), any(CachedReportResult.class), eq(ttl), eq(TimeUnit.SECONDS));
    }

    @Test
    void zeroReportsAndEmptyItemListsAreStillCached() {
        String key = cache.key(View.REPORT_TOP10, filter());
        AtomicInteger loads = new AtomicInteger();
        Supplier<Map<String, Object>> loader = () -> {
            loads.incrementAndGet();
            Map<String, Object> data = top10Data();
            data.put("items", Collections.emptyList());
            return data;
        };

        cache.getOrLoad(key, View.REPORT_TOP10, 120L, loader);
        cache.getOrLoad(key, View.REPORT_TOP10, 120L, loader);

        assertEquals(1, loads.get());
    }

    @Test
    void disabledCacheNeverCallsRedis() {
        ReportCacheProperties disabled = ReportTestSupport.properties(false);
        ReportResultCache disabledCache = new ReportResultCache(redis, disabled);
        AtomicInteger loads = new AtomicInteger();
        Supplier<Map<String, Object>> loader = () -> {
            loads.incrementAndGet();
            return turnoverData();
        };

        disabledCache.getOrLoad("k", View.REPORT_TURNOVER, 60L, loader);
        disabledCache.getOrLoad("k", View.REPORT_TURNOVER, 60L, loader);

        assertEquals(2, loads.get());
        verifyNoInteractions(redis);
    }

    @Test
    void redisReadFailureFallsBackToTheDatabaseOnceAndSkipsTheWrite() {
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("test only"));
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> result = cache.getOrLoad("k", View.REPORT_TURNOVER, 60L, () -> {
            loads.incrementAndGet();
            return turnoverData();
        });

        assertEquals(1, loads.get());
        assertEquals("0.00,30.00", result.get("netList"));
        verify(ops, never()).setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void failedNxWriteDoesNotTriggerASecondDatabaseRead() {
        when(ops.get(anyString())).thenReturn(null);
        when(ops.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> result = cache.getOrLoad("k", View.REPORT_TURNOVER, 60L, () -> {
            loads.incrementAndGet();
            return turnoverData();
        });

        assertEquals(1, loads.get());
        assertEquals("0.00,30.00", result.get("netList"));
    }

    @Test
    void redisWriteFailureStillReturnsTheDatabaseResult() {
        doThrow(new RedisConnectionFailureException("test only"))
                .when(ops).setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class));
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> result = cache.getOrLoad("k", View.REPORT_TURNOVER, 60L, () -> {
            loads.incrementAndGet();
            return turnoverData();
        });

        assertEquals(1, loads.get());
        assertEquals("2026-11-01,2026-11-02", result.get("dateList"));
    }

    @Test
    void loaderFailuresPropagateAndAreNeverCached() {
        AtomicInteger loads = new AtomicInteger();

        assertThrows(OrderBusinessException.class, () -> cache.getOrLoad("k", View.REPORT_TURNOVER, 60L, () -> {
            loads.incrementAndGet();
            throw new OrderBusinessException("明细超过50000条，请缩小日期或门店范围");
        }));

        assertEquals(1, loads.get());
        assertFalse(store.containsKey("k"));
        verify(ops, never()).setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void nullWrongSchemaAndMalformedPayloadsAreMisses() {
        String key = cache.key(View.WORKSPACE_BUSINESS, filter());
        AtomicInteger loads = new AtomicInteger();
        Supplier<Map<String, Object>> loader = () -> {
            loads.incrementAndGet();
            return businessData();
        };

        assertTrue(cache.getOrLoad(key, View.WORKSPACE_BUSINESS, 15L, loader).containsKey("netAmount"));
        assertEquals(1, loads.get());

        store.put(key, "not-an-envelope");
        assertNotNull(cache.getOrLoad(key, View.WORKSPACE_BUSINESS, 15L, loader));
        assertEquals(2, loads.get());

        store.put(key, new CachedReportResult(2, businessData()));
        assertNotNull(cache.getOrLoad(key, View.WORKSPACE_BUSINESS, 15L, loader));
        assertEquals(3, loads.get());

        Map<String, Object> wrongType = new LinkedHashMap<>(businessData());
        wrongType.put("turnover", "170.00");
        store.put(key, new CachedReportResult(wrongType));
        assertNotNull(cache.getOrLoad(key, View.WORKSPACE_BUSINESS, 15L, loader));
        assertEquals(4, loads.get());

        store.put(key, new CachedReportResult(null));
        assertNotNull(cache.getOrLoad(key, View.WORKSPACE_BUSINESS, 15L, loader));
        assertEquals(5, loads.get());
    }

    @Test
    void cachedViewMismatchIsAMiss() {
        String key = cache.key(View.REPORT_TURNOVER, filter());
        store.put(key, new CachedReportResult(businessData()));
        AtomicInteger loads = new AtomicInteger();

        cache.getOrLoad(key, View.REPORT_TURNOVER, 120L, () -> {
            loads.incrementAndGet();
            return turnoverData();
        });

        assertEquals(1, loads.get());
    }

    @Test
    void top10RequiresRealReportRowsAndKeepsItemIdentity() {
        String key = cache.key(View.REPORT_TOP10, filter());
        AtomicInteger loads = new AtomicInteger();
        Supplier<Map<String, Object>> loader = () -> {
            loads.incrementAndGet();
            return top10Data();
        };

        cache.getOrLoad(key, View.REPORT_TOP10, 120L, loader);
        Map<String, Object> cached = cache.getOrLoad(key, View.REPORT_TOP10, 120L, loader);
        assertEquals(1, loads.get());
        ReportRow row = (ReportRow) ((java.util.List<?>) cached.get("items")).get(0);
        assertEquals(9007199254740993L, row.getDishId().longValue());

        Map<String, Object> stringItems = new LinkedHashMap<>(top10Data());
        stringItems.put("items", Collections.singletonList("fish:2"));
        store.put(key, new CachedReportResult(stringItems));
        cache.getOrLoad(key, View.REPORT_TOP10, 120L, loader);
        assertEquals(2, loads.get());
    }

    @Test
    void ttlIsNeverWrittenAsZeroOrNegative() {
        cache.getOrLoad("k", View.REPORT_TURNOVER, 0L, ReportResultCacheTest::turnoverData);
        verify(ops).setIfAbsent(eq("k"), any(CachedReportResult.class), eq(1L), eq(TimeUnit.SECONDS));

        reset(ops);
        when(ops.get(anyString())).thenReturn(null);
        when(ops.setIfAbsent(anyString(), any(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        cache.getOrLoad("k2", View.REPORT_TURNOVER, -5L, ReportResultCacheTest::turnoverData);
        verify(ops).setIfAbsent(eq("k2"), any(CachedReportResult.class), eq(1L), eq(TimeUnit.SECONDS));
    }

    @Test
    void liveRangesUseTheShortTtl() {
        ReportFilter live = new ReportFilter(1L, Arrays.asList(1L),
                LocalDateTime.of(2026, 11, 17, 0, 0), LocalDateTime.of(2026, 11, 18, 0, 0));
        long ttl = cache.ttlSeconds(View.REPORT_ORDERS, live, ReportTestSupport.TODAY);
        assertTrue(ttl >= 13L && ttl <= 15L, "live ttl " + ttl);
    }

    @Test
    void keyContainsTheConfiguredNamespace() {
        assertTrue(cache.key(View.REPORT_USERS, filter()).startsWith("junzhou:dev:report-cache:v1:tenant:1:view:report-users:"));
        ReportCacheProperties other = ReportTestSupport.properties(true);
        other.setNamespace("prod_2");
        assertTrue(new ReportResultCache(redis, other).key(View.REPORT_USERS, filter())
                .startsWith("junzhou:prod_2:report-cache:v1:"));
    }
}
