package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;
import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import com.junzhou.test.support.CountingReportMapper;
import com.junzhou.test.support.InMemoryRedis;
import com.junzhou.test.support.ReportTestDatabase;
import com.junzhou.test.support.ReportTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Report views: authorized cache reads, scope isolation, statistics parity and TTL selection. */
class ReportReadCacheTest {
    private ReportTestDatabase db;
    private CountingReportMapper mapper;
    private RbacService rbac;
    private InMemoryRedis redis;
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        db = ReportTestDatabase.create();
        mapper = new CountingReportMapper(db.mapper());
        rbac = mock(RbacService.class);
        when(rbac.permits(anyLong(), anyLong(), anyLong(), any(PermissionAction.class))).thenReturn(true);
        redis = new InMemoryRedis();
        service = new ReportServiceImpl(mapper, rbac,
                new ReportResultCache(redis.template, ReportTestSupport.properties(true)), ReportTestSupport.fixedToday());
        RequestScope.set(new RequestScope(1L, null, 1L, 1L, new HashSet<>(Arrays.asList(1L, 2L))));
    }

    @AfterEach
    void cleanup() {
        RequestScope.clear();
        if (db != null) db.close();
    }

    private static void employee(long employeeId, long store) {
        RequestScope.set(new RequestScope(employeeId, null, 1L, store,
                new HashSet<>(Arrays.asList(1L, 2L))));
    }

    private ReportFilter range(String begin, String end, List<Long> stores) {
        return service.filter(begin, end, stores, false);
    }

    @Test
    void everyReportViewHitsItsOwnCacheOnTheSecondCall() {
        ReportFilter filter = range("2026-11-01", "2026-11-03", null);
        Map<String, Object> turnover = service.turnover(filter);
        Map<String, Object> orders = service.orders(filter);
        Map<String, Object> users = service.users(filter);
        Map<String, Object> top10 = service.top10(filter);
        assertEquals(3, mapper.calls("daily"), "turnover, orders and users each fill their own day series");
        assertEquals(1, mapper.calls("overview"));
        assertEquals(1, mapper.calls("users"));
        assertEquals(1, mapper.calls("sales"));
        int sqlCalls = mapper.total();

        assertEquals(turnover, service.turnover(filter));
        assertEquals(orders, service.orders(filter));
        assertEquals(users, service.users(filter));
        assertEquals(top10, service.top10(filter));
        assertEquals(sqlCalls, mapper.total(), "all four charts must be served from cache");
        assertEquals(4, redis.size());
    }

    @Test
    void dailyZeroFillRefundsRankingAndCohortsMatchTheOriginalComputation() {
        ReportFilter filter = range("2026-11-01", "2026-11-03", null);
        Map<String, Object> turnover = service.turnover(filter);
        assertEquals("2026-11-01,2026-11-02,2026-11-03", turnover.get("dateList"));
        assertEquals("0.00,30.00,0.00", turnover.get("turnoverList"));
        assertEquals("0.00,0.00,10.00", turnover.get("refundList"));
        assertEquals("0.00,30.00,-10.00", turnover.get("netList"));

        Map<String, Object> orders = service.orders(filter);
        assertEquals("2,0,0", orders.get("orderCountList"));
        assertEquals("0,2,0", orders.get("paidOrderCountList"));
        assertEquals("0,1,0", orders.get("validOrderCountList"));
        assertEquals("1,0,0", orders.get("cancelledOrderCountList"));
        assertEquals(2L, ((Number) orders.get("totalOrderCount")).longValue());
        assertEquals(1L, ((Number) orders.get("validOrderCount")).longValue());
        assertEquals(2L, ((Number) orders.get("paidOrderCount")).longValue());
        assertEquals(1L, ((Number) orders.get("cancelledOrderCount")).longValue());
        assertEquals(0.5d, ((Number) orders.get("orderCompletionRate")).doubleValue());

        Map<String, Object> users = service.users(filter);
        assertEquals("2026-11-01,2026-11-02,2026-11-03", users.get("dateList"));
        assertEquals("2,0,0", users.get("newUserList"));
        assertEquals("2,2,2", users.get("totalUserList"));

        Map<String, Object> top10 = service.top10(filter);
        assertEquals("late", top10.get("nameList"));
        assertEquals("1", top10.get("numberList"));

        // The same numbers must come back from the cache.
        assertEquals(turnover, service.turnover(filter));
        assertEquals(orders, service.orders(filter));
        assertEquals(users, service.users(filter));
        assertEquals(top10, service.top10(filter));
    }

    @Test
    @SuppressWarnings("unchecked")
    void top10ItemsKeepTheirIdentityThroughTheCache() {
        ReportFilter wide = range("2026-11-02", "2026-11-02", Arrays.asList(1L, 2L));
        Map<String, Object> cold = service.top10(wide);
        Map<String, Object> hit = service.top10(wide);

        assertEquals("fish,meal,late", cold.get("nameList"));
        assertEquals("fish,meal,late", hit.get("nameList"));
        List<ReportRow> items = (List<ReportRow>) hit.get("items");
        assertEquals(3, items.size());
        assertEquals("fish", items.get(0).getName());
        assertEquals(1L, items.get(0).getDishId().longValue());
        assertEquals(2L, items.get(0).getQuantity());
        assertEquals("meal", items.get(1).getName());
        assertEquals(1L, items.get(1).getSetmealId().longValue());
        assertNull(items.get(1).getDishId());
        assertEquals(2L, items.get(2).getDishId().longValue());
    }

    @Test
    void customerCohortsRemainScopedToTheSelectedStoreSet() {
        ReportFilter singleStore = range("2026-11-02", "2026-11-02", Collections.singletonList(1L));
        assertEquals("0", service.users(singleStore).get("newUserList"));
        assertEquals("2", service.users(singleStore).get("totalUserList"));

        ReportFilter bothStores = range("2026-11-02", "2026-11-02", Arrays.asList(1L, 2L));
        assertEquals("1", service.users(bothStores).get("newUserList"));
        assertEquals("3", service.users(bothStores).get("totalUserList"),
                "the customer who first ordered at store 2 counts once the store set includes it");
    }

    @Test
    void differentRangesStoreSetsAndViewsNeverShareACacheEntry() {
        ReportFilter narrow = range("2026-11-01", "2026-11-02", null);
        ReportFilter wide = range("2026-11-01", "2026-11-03", null);
        ReportFilter bothStores = range("2026-11-01", "2026-11-02", Arrays.asList(1L, 2L));

        assertEquals("0.00,30.00", service.turnover(narrow).get("turnoverList"));
        assertEquals("0.00,60.00", service.turnover(bothStores).get("turnoverList"));
        assertEquals("0.00,30.00,0.00", service.turnover(wide).get("turnoverList"));
        assertEquals(3, redis.size());

        // The orders chart for the same scope is a different view and needs its own entry.
        assertNotNull(service.orders(narrow).get("dateList"));
        assertEquals(4, redis.size());
    }

    @Test
    void warmCacheCannotBypassRevokedStorePermission() {
        ReportFilter filter = range("2026-11-01", "2026-11-03", Arrays.asList(1L, 2L));
        service.turnover(filter);
        int sqlCalls = mapper.total();
        int reads = redis.reads.get();

        when(rbac.permits(1L, 1L, 2L, PermissionAction.REPORT_READ)).thenReturn(false);
        assertThrows(SecurityException.class, () -> service.turnover(filter));
        assertEquals(sqlCalls, mapper.total());
        assertEquals(reads, redis.reads.get(), "a denied request must be rejected before the cache");

        // Another employee without access must not inherit the first employee's warm cache either.
        employee(9L, 1L);
        when(rbac.permits(1L, 9L, 1L, PermissionAction.REPORT_READ)).thenReturn(false);
        assertThrows(SecurityException.class, () -> service.turnover(filter));
        assertEquals(sqlCalls, mapper.total());
        assertEquals(reads, redis.reads.get());
    }

    @Test
    void directServiceCallsCannotBypassStaleOrForgedFilters() {
        // Forged tenant against the trusted request scope.
        ReportFilter forged = new ReportFilter(2L, Collections.singletonList(1L),
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0));
        assertThrows(SecurityException.class, () -> service.turnover(forged));

        // Empty store set.
        ReportFilter empty = new ReportFilter(1L, Collections.emptyList(),
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0));
        assertThrows(SecurityException.class, () -> service.turnover(empty));

        // Missing identity entirely.
        RequestScope.clear();
        assertThrows(SecurityException.class, () -> service.turnover(forged));

        assertEquals(0, mapper.total());
        assertEquals(0, redis.reads.get());
    }

    @Test
    void rangesAndFiltersThatCannotBeKeyedAreRejectedBeforeRedis() {
        employee(1L, 1L);
        ReportFilter tooLong = new ReportFilter(1L, Collections.singletonList(1L),
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 4, 5, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.turnover(tooLong));

        ReportFilter misaligned = new ReportFilter(1L, Collections.singletonList(1L),
                LocalDateTime.of(2026, 11, 1, 9, 0), LocalDateTime.of(2026, 11, 4, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.turnover(misaligned));

        ReportFilter reversed = new ReportFilter(1L, Collections.singletonList(1L),
                LocalDateTime.of(2026, 11, 4, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.turnover(reversed));

        ReportFilter tooManyStores = new ReportFilter(1L, manyStores(),
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.turnover(tooManyStores));

        assertEquals(0, mapper.total());
        assertEquals(0, redis.reads.get());
    }

    private static List<Long> manyStores() {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (long id = 1; id <= 101; id++) ids.add(id);
        return ids;
    }

    @Test
    void historicalRangesUseTheLongTtlAndLiveRangesTheShortOne() {
        ReportCacheProperties properties = ReportTestSupport.properties(true);
        ReportResultCache cache = new ReportResultCache(redis.template, properties);

        long historical = cache.ttlSeconds(com.junzhou.report.cache.ReportCacheKey.View.REPORT_TURNOVER,
                range("2026-11-01", "2026-11-03", null), ReportTestSupport.TODAY);
        assertTrue(historical >= 118L && historical <= 120L, "historical ttl " + historical);
        long live = cache.ttlSeconds(com.junzhou.report.cache.ReportCacheKey.View.REPORT_TURNOVER,
                range("2026-11-16", "2026-11-17", null), ReportTestSupport.TODAY);
        assertTrue(live >= 13L && live <= 15L, "live ttl " + live);
        long workspace = cache.ttlSeconds(com.junzhou.report.cache.ReportCacheKey.View.WORKSPACE_ORDERS,
                new ReportFilter(1L, Collections.singletonList(1L),
                        LocalDateTime.of(2026, 11, 17, 0, 0), LocalDateTime.of(2026, 11, 18, 0, 0)),
                ReportTestSupport.TODAY);
        assertTrue(workspace >= 13L && workspace <= 15L, "workspace ttl " + workspace);

        // The TTL handed to Redis is the jittered one, never 0.
        redis = new InMemoryRedis();
        service = new ReportServiceImpl(mapper, rbac,
                new ReportResultCache(redis.template, ReportTestSupport.properties(true)), ReportTestSupport.fixedToday());
        service.turnover(range("2026-11-01", "2026-11-03", null));
        assertTrue(redis.lastTtlSeconds.get() >= 118L && redis.lastTtlSeconds.get() <= 120L);
    }

    @Test
    void historicalChangesAppearOnceTheEntryExpires() {
        ReportFilter filter = range("2026-11-01", "2026-11-03", null);
        assertEquals("0.00,0.00,10.00", service.turnover(filter).get("refundList"));

        // Confirmed refund on 2026-11-02 for the same store and a new paid order arrive after the chart was cached.
        db.jdbc().update("insert into orders values(5,1,1,'e','2026-11-02 09:00','2026-11-02 09:10',null,null,100,3,5,'DELIVERY')");
        db.jdbc().update("insert into refund_request values(5,'SUCCESS','2026-11-02 18:00',500)");

        assertEquals("0.00,0.00,10.00", service.turnover(filter).get("refundList"),
                "within the TTL the cached snapshot is served unchanged");

        // Simulate the TTL expiry: the next request rebuilds from the database.
        redis.values.clear();
        Map<String, Object> rebuilt = service.turnover(filter);
        assertEquals("0.00,130.00,0.00", rebuilt.get("turnoverList"));
        assertEquals("0.00,5.00,10.00", rebuilt.get("refundList"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sameNameDifferentItemIdsAndEmptyRankingsKeepTheirSemantics() {
        // Two different product ids share one snapshot name; identity must not be merged.
        db.jdbc().update("insert into order_detail values(6,2,11,null,'同名',1,5),(7,2,null,12,'同名',1,5)");
        ReportFilter filter = range("2026-11-01", "2026-11-03", Arrays.asList(1L, 2L));
        Map<String, Object> top10 = service.top10(filter);
        List<ReportRow> items = (List<ReportRow>) top10.get("items");
        assertTrue(items.stream().anyMatch(row -> Long.valueOf(11L).equals(row.getDishId())));
        assertTrue(items.stream().anyMatch(row -> Long.valueOf(12L).equals(row.getSetmealId())));
        assertEquals(2, countOccurrences((String) top10.get("nameList"), "同名"));
        assertEquals(top10, service.top10(filter));

        // A range without sales is an empty ranking, and it is still cacheable.
        ReportFilter quiet = range("2026-10-01", "2026-10-02", null);
        Map<String, Object> empty = service.top10(quiet);
        assertEquals("", empty.get("nameList"));
        assertEquals("", empty.get("numberList"));
        assertTrue(((List<?>) empty.get("items")).isEmpty());
        int salesCalls = mapper.calls("sales");
        assertEquals(empty, service.top10(quiet));
        assertEquals(salesCalls, mapper.calls("sales"));
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0; index = value.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void everyViewPayloadIsJdkSerializableAsStored() throws Exception {
        ReportFilter filter = range("2026-11-01", "2026-11-03", Arrays.asList(1L, 2L));
        java.util.List<Map<String, Object>> payloads = Arrays.asList(
                service.turnover(filter), service.orders(filter), service.users(filter), service.top10(filter));
        for (Map<String, Object> payload : payloads) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes)) {
                out.writeObject(new com.junzhou.report.cache.CachedReportResult(payload));
            }
            assertTrue(bytes.size() > 0);
        }
    }

    @Test
    void disabledCacheNeverReadsOrWritesRedis() {
        ReportServiceImpl uncached = new ReportServiceImpl(mapper, rbac,
                new ReportResultCache(redis.template, ReportTestSupport.properties(false)), ReportTestSupport.fixedToday());
        ReportFilter filter = uncached.filter("2026-11-01", "2026-11-03", null, false);
        uncached.turnover(filter);
        int sqlCalls = mapper.total();
        uncached.turnover(filter);
        assertEquals(sqlCalls + 1, mapper.calls("daily"));
        assertEquals(0, redis.reads.get());
        assertEquals(0, redis.writes.get());
    }
}
