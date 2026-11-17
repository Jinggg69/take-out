package com.junzhou.test;

import com.alibaba.fastjson.JSON;
import com.junzhou.config.RedisConfiguration;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;
import com.junzhou.report.cache.CachedReportResult;
import com.junzhou.report.cache.ReportCacheKey.View;
import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-Redis verification of the production JDK serializer: money, identifiers, rates, nested rows,
 * finite TTL, expiry rebuild and cross-instance sharing. Requires the local Redis from scripts/redis.ps1.
 */
@SpringBootTest(
        classes = {RedisConfiguration.class, RedisAutoConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ReportCacheRedisTest {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final List<String> keys = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // Delete only keys created by this test; never FLUSHDB or scan business keys.
        if (!keys.isEmpty()) redisTemplate.delete(keys);
        keys.clear();
    }

    private static ReportFilter filter() {
        return new ReportFilter(1L, Arrays.asList(1L, 2L),
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 17, 0, 0));
    }

    private ReportCacheProperties properties(long ttlSeconds) {
        ReportCacheProperties properties = new ReportCacheProperties();
        properties.setNamespace("test" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        properties.setWorkspaceTtlSeconds(ttlSeconds);
        properties.setLiveReportTtlSeconds(ttlSeconds);
        properties.setHistoricalTtlSeconds(ttlSeconds);
        properties.setJitterSeconds(0L);
        properties.afterPropertiesSet();
        return properties;
    }

    private String key(ReportResultCache cache, View view) {
        String key = cache.key(view, filter());
        keys.add(key);
        return key;
    }

    private static ReportRow row(long dishId, String name, long quantity) {
        ReportRow row = new ReportRow();
        row.setDishId(dishId);
        row.setName(name);
        row.setQuantity(quantity);
        return row;
    }

    private static Map<String, Object> top10Payload() {
        ReportRow first = row(9007199254740993L, "fish", 2L);
        ReportRow second = row(7L, "meal", 1L);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nameList", "fish,meal");
        data.put("numberList", "2,1");
        data.put("items", Arrays.asList(first, second));
        // Values shared with the other report contracts, kept here to prove the serializer round-trip.
        data.put("net", new BigDecimal("12.50"));
        data.put("orderCompletionRate", 0.25d);
        data.put("totalOrderCount", 4L);
        return data;
    }

    @Test
    void jdkSerializerRoundTripsMoneyIdentifiersRatesAndNestedRows() {
        ReportResultCache cache = new ReportResultCache(redisTemplate, properties(120L));
        String key = key(cache, View.REPORT_TOP10);
        AtomicInteger loads = new AtomicInteger();

        Map<String, Object> stored = cache.getOrLoad(key, View.REPORT_TOP10, 120L, () -> {
            loads.incrementAndGet();
            return top10Payload();
        });
        Map<String, Object> hit = cache.getOrLoad(key, View.REPORT_TOP10, 120L, () -> {
            loads.incrementAndGet();
            return Collections.emptyMap();
        });

        assertEquals(1, loads.get());
        assertNotSame(stored, hit);
        BigDecimal net = (BigDecimal) hit.get("net");
        assertEquals(new BigDecimal("12.50"), net);
        assertEquals(2, net.scale());
        assertEquals(0.25d, ((Double) hit.get("orderCompletionRate")).doubleValue());
        assertEquals(4L, ((Number) hit.get("totalOrderCount")).longValue());
        assertEquals("fish,meal", hit.get("nameList"));

        @SuppressWarnings("unchecked")
        List<ReportRow> items = (List<ReportRow>) hit.get("items");
        assertEquals(2, items.size());
        assertEquals(9007199254740993L, items.get(0).getDishId().longValue());
        assertEquals(2L, items.get(0).getQuantity());
        assertEquals("fish", items.get(0).getName());
        assertEquals(7L, items.get(1).getDishId().longValue());

        // The API JSON must be semantically identical before and after the round-trip, precision included.
        assertEquals(JSON.toJSONString(stored), JSON.toJSONString(hit));
        assertTrue(JSON.toJSONString(hit).contains("\"net\":12.50"));
        assertTrue(JSON.toJSONString(hit).contains("9007199254740993"));
    }

    @Test
    void everyWriteHasFiniteTtlHitsDoNotRenewAndExpiryRebuildsFromDatabase() throws Exception {
        ReportResultCache cache = new ReportResultCache(redisTemplate, properties(2L));
        String key = key(cache, View.REPORT_TURNOVER);
        AtomicInteger loads = new AtomicInteger();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dateList", "2026-11-01");
        payload.put("turnoverList", "0.00");
        payload.put("refundList", "0.00");
        payload.put("netList", "0.00");

        cache.getOrLoad(key, View.REPORT_TURNOVER, 2L, () -> {
            loads.incrementAndGet();
            return payload;
        });
        Long initialTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(initialTtl);
        assertTrue(initialTtl > 0 && initialTtl <= 2, "finite ttl " + initialTtl);

        Thread.sleep(1100L);
        Long beforeHit = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        cache.getOrLoad(key, View.REPORT_TURNOVER, 2L, () -> {
            loads.incrementAndGet();
            return payload;
        });
        Long afterHit = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertEquals(1, loads.get());
        assertNotNull(beforeHit);
        assertNotNull(afterHit);
        assertTrue(afterHit <= beforeHit, "a cache hit must not renew the ttl");

        long deadline = System.currentTimeMillis() + 6000L;
        while (Boolean.TRUE.equals(redisTemplate.hasKey(key)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(150L);
        }
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)), "cache key must expire");

        Map<String, Object> rebuilt = cache.getOrLoad(key, View.REPORT_TURNOVER, 2L, () -> {
            loads.incrementAndGet();
            Map<String, Object> fresh = new LinkedHashMap<>(payload);
            fresh.put("turnoverList", "30.00");
            return fresh;
        });
        assertEquals(2, loads.get());
        assertEquals("30.00", rebuilt.get("turnoverList"));
        assertTrue(redisTemplate.getExpire(key, TimeUnit.SECONDS) > 0);
    }

    @Test
    void twoInstancesShareRedisAndDifferentKeysStayIsolated() {
        ReportCacheProperties shared = properties(120L);
        ReportResultCache first = new ReportResultCache(redisTemplate, shared);
        ReportResultCache second = new ReportResultCache(redisTemplate, shared);
        String turnoverKey = key(first, View.REPORT_TURNOVER);
        String ordersKey = key(first, View.REPORT_ORDERS);
        AtomicInteger secondLoads = new AtomicInteger();

        first.getOrLoad(turnoverKey, View.REPORT_TURNOVER, 120L, () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dateList", "2026-11-01");
            data.put("turnoverList", "0.00");
            data.put("refundList", "0.00");
            data.put("netList", "0.00");
            return data;
        });
        Map<String, Object> fromSecond = second.getOrLoad(turnoverKey, View.REPORT_TURNOVER, 120L, () -> {
            secondLoads.incrementAndGet();
            return Collections.emptyMap();
        });
        assertEquals(0, secondLoads.get());
        assertEquals("0.00", fromSecond.get("netList"));

        Map<String, Object> orders = new LinkedHashMap<>();
        orders.put("dateList", "2026-11-01");
        orders.put("orderCountList", "2");
        orders.put("paidOrderCountList", "0");
        orders.put("validOrderCountList", "0");
        orders.put("cancelledOrderCountList", "0");
        orders.put("totalOrderCount", 2L);
        orders.put("validOrderCount", 0L);
        orders.put("orderCompletionRate", 0.0d);
        orders.put("paidOrderCount", 0L);
        orders.put("cancelledOrderCount", 0L);
        second.getOrLoad(ordersKey, View.REPORT_ORDERS, 120L, () -> orders);

        assertNotEquals(turnoverKey, ordersKey);
        CachedReportResult cachedTurnover = (CachedReportResult) redisTemplate.opsForValue().get(turnoverKey);
        CachedReportResult cachedOrders = (CachedReportResult) redisTemplate.opsForValue().get(ordersKey);
        assertEquals("0.00", cachedTurnover.getData().get("netList"));
        assertEquals(2L, ((Number) cachedOrders.getData().get("totalOrderCount")).longValue());
        assertNull(cachedOrders.getData().get("netList"));
    }

    @Test
    void nxKeepsTheSnapshotThatFinishedEarlier() {
        ReportResultCache cache = new ReportResultCache(redisTemplate, properties(120L));
        String key = key(cache, View.REPORT_TURNOVER);
        Map<String, Object> winner = new LinkedHashMap<>();
        winner.put("dateList", "winner");
        winner.put("turnoverList", "1.00");
        winner.put("refundList", "0.00");
        winner.put("netList", "1.00");

        Map<String, Object> result = cache.getOrLoad(key, View.REPORT_TURNOVER, 120L, () -> {
            // A slower loader finishing after another writer already stored a snapshot must not overwrite it.
            redisTemplate.opsForValue().set(key, new CachedReportResult(winner), 120L, TimeUnit.SECONDS);
            Map<String, Object> loser = new LinkedHashMap<>();
            loser.put("dateList", "loser");
            loser.put("turnoverList", "2.00");
            loser.put("refundList", "0.00");
            loser.put("netList", "2.00");
            return loser;
        });

        assertEquals("loser", result.get("dateList"));
        CachedReportResult stored = (CachedReportResult) redisTemplate.opsForValue().get(key);
        assertEquals("winner", stored.getData().get("dateList"));
    }

    @Test
    void malformedSerializedValueIsAMissAndIsLeftToExpire() {
        ReportResultCache cache = new ReportResultCache(redisTemplate, properties(120L));
        String key = key(cache, View.REPORT_TURNOVER);
        redisTemplate.opsForValue().set(key, "not-an-envelope", 30L, TimeUnit.SECONDS);
        AtomicInteger loads = new AtomicInteger();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dateList", "2026-11-01");
        payload.put("turnoverList", "0.00");
        payload.put("refundList", "0.00");
        payload.put("netList", "0.00");

        Map<String, Object> result = cache.getOrLoad(key, View.REPORT_TURNOVER, 120L, () -> {
            loads.incrementAndGet();
            return payload;
        });

        assertEquals(1, loads.get());
        assertEquals("2026-11-01", result.get("dateList"));
        assertEquals("not-an-envelope", redisTemplate.opsForValue().get(key));
    }
}
