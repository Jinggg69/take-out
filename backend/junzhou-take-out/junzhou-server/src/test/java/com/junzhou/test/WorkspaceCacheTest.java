package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.controller.admin.WorkspaceController;
import com.junzhou.interceptor.RbacInterceptor;
import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import com.junzhou.test.support.CountingReportMapper;
import com.junzhou.test.support.InMemoryRedis;
import com.junzhou.test.support.MutableClock;
import com.junzhou.test.support.ReportTestDatabase;
import com.junzhou.test.support.ReportTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Workspace views: cache after authorization, per-request RBAC recheck, isolation and midnight rollover. */
class WorkspaceCacheTest {
    private ReportTestDatabase db;
    private CountingReportMapper mapper;
    private RbacService rbac;
    private InMemoryRedis redis;
    private MutableClock clock;
    private ReportServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        db = ReportTestDatabase.create();
        mapper = new CountingReportMapper(db.mapper());
        rbac = mock(RbacService.class);
        redis = new InMemoryRedis();
        clock = new MutableClock(ReportTestSupport.TODAY.atTime(12, 0));
        service = new ReportServiceImpl(mapper, rbac,
                new ReportResultCache(redis.template, ReportTestSupport.properties(true)), clock);
        mvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(service))
                .addInterceptors(new RbacInterceptor(rbac)).build();
        RequestScope.set(new RequestScope(1L, null, 1L, 10L, new HashSet<>(Arrays.asList(10L, 11L))));
    }

    @AfterEach
    void cleanup() {
        RequestScope.clear();
        if (db != null) db.close();
    }

    private static void workspaceEmployee(long employee, long store) {
        RequestScope.set(new RequestScope(employee, null, 1L, store,
                new HashSet<>(Collections.singletonList(store))));
    }

    @Test
    void businessDataKeepsItsFieldsAndIsServedFromCacheTheSecondTime() throws Exception {
        mvc.perform(get("/admin/workspace/businessData"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.turnover").value(170.0))
                .andExpect(jsonPath("$.data.refundAmount").value(10.0))
                .andExpect(jsonPath("$.data.netAmount").value(160.0))
                .andExpect(jsonPath("$.data.validOrderCount").value(1))
                .andExpect(jsonPath("$.data.paidOrderCount").value(3))
                .andExpect(jsonPath("$.data.placedOrderCount").value(4))
                .andExpect(jsonPath("$.data.cancelledOrderCount").value(1))
                .andExpect(jsonPath("$.data.orderCompletionRate").value(0.25))
                .andExpect(jsonPath("$.data.unitPrice").value(56.67))
                .andExpect(jsonPath("$.data.newUsers").value(4));
        int sqlCalls = mapper.total();
        assertEquals(3, sqlCalls, "daily, users and status distribution on a cold view");

        mvc.perform(get("/admin/workspace/businessData"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netAmount").value(160.0));

        assertEquals(sqlCalls, mapper.total(), "a cache hit must not query the report mapper");
        assertEquals(1, redis.writes.get());
    }

    @Test
    void overviewOrdersKeepsStatusDistributionAndIsCached() throws Exception {
        mvc.perform(get("/admin/workspace/overviewOrders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.waitingOrders").value(1))
                .andExpect(jsonPath("$.data.deliveredOrders").value(1))
                .andExpect(jsonPath("$.data.completedOrders").value(1))
                .andExpect(jsonPath("$.data.cancelledOrders").value(1))
                .andExpect(jsonPath("$.data.allOrders").value(4));
        mvc.perform(get("/admin/workspace/overviewOrders")).andExpect(status().isOk());
        assertEquals(1, mapper.calls("overview"));
    }

    @Test
    void dishAndSetmealCountsAreCachedUnderDifferentViews() throws Exception {
        mvc.perform(get("/admin/workspace/overviewDishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sold").value(2))
                .andExpect(jsonPath("$.data.discontinued").value(3));
        mvc.perform(get("/admin/workspace/overviewDishes")).andExpect(status().isOk());
        assertEquals(1, mapper.calls("menu"));

        mvc.perform(get("/admin/workspace/overviewSetmeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sold").value(1))
                .andExpect(jsonPath("$.data.discontinued").value(1));
        mvc.perform(get("/admin/workspace/overviewSetmeals")).andExpect(status().isOk());
        assertEquals(2, mapper.calls("menu"), "dish and setmeal counts must not share one cached value");
    }

    @Test
    void emptyStoreCountsAreZeroAndCached() throws Exception {
        RequestScope.set(new RequestScope(1L, null, 1L, 12L, Collections.singleton(12L)));
        mvc.perform(get("/admin/workspace/overviewDishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sold").value(0))
                .andExpect(jsonPath("$.data.discontinued").value(0));
        mvc.perform(get("/admin/workspace/overviewDishes")).andExpect(status().isOk());
        assertEquals(1, mapper.calls("menu"));
    }

    @Test
    void authorizationIsRecheckedOnEveryHitAndRevocationDeniesWithoutReadingTheCache() {
        service.business();
        int sqlCalls = mapper.total();
        int redisReads = redis.reads.get();

        doThrow(new SecurityException("revoked")).when(rbac).require(PermissionAction.WORKSPACE_READ);
        assertThrows(SecurityException.class, () -> service.business());

        assertEquals(sqlCalls, mapper.total());
        assertEquals(redisReads, redis.reads.get(), "a denied request must not touch the report cache");
    }

    @Test
    void workspacePermissionAloneIsEnoughEvenWithoutReportPermission() {
        when(rbac.permits(anyLong(), anyLong(), anyLong(), any(PermissionAction.class))).thenReturn(false);
        assertFalse(service.business().isEmpty());
        assertFalse(service.menu("dish").isEmpty());
        verify(rbac, never()).require(PermissionAction.REPORT_READ);
        verify(rbac, never()).require(PermissionAction.REPORT_EXPORT);
    }

    @Test
    void authorizedEmployeesOfTheSameStoreShareButOtherStoresAndTenantsDoNot() {
        workspaceEmployee(1L, 10L);
        service.menu("dish");
        int afterFirst = mapper.calls("menu");

        // A different employee with the same store reads the shared display value.
        workspaceEmployee(2L, 10L);
        service.menu("dish");
        assertEquals(afterFirst, mapper.calls("menu"));

        // A different store gets its own key.
        RequestScope.set(new RequestScope(1L, null, 1L, 11L, Collections.singleton(11L)));
        assertEquals(1L, ((Number) service.menu("dish").get("sold")).longValue());
        assertEquals(afterFirst + 1, mapper.calls("menu"));

        // A different tenant gets its own key and its own data.
        RequestScope.set(new RequestScope(1L, null, 2L, 20L, Collections.singleton(20L)));
        assertEquals(1L, ((Number) service.menu("dish").get("sold")).longValue());
        assertEquals(afterFirst + 2, mapper.calls("menu"));
        assertEquals(3, redis.size());
    }

    @Test
    void missingScopeIsRejected() {
        RequestScope.clear();
        assertThrows(SecurityException.class, () -> service.business());
        assertEquals(0, mapper.total());
        assertEquals(0, redis.reads.get());
    }

    @Test
    void midnightRolloverUsesNewDatesAndANewKey() {
        clock.set(ReportTestSupport.TODAY.atTime(23, 59, 59));
        service.business();
        String firstKey = redis.onlyKey();
        assertTrue(firstKey.contains("from:20261117T000000:until:20261118T000000"), firstKey);

        clock.set(ReportTestSupport.TODAY.plusDays(1).atTime(0, 0, 1));
        java.util.Map<String, Object> nextDay = service.business();

        assertEquals(2, redis.size(), "a new calendar day must use a new workspace key");
        assertEquals(0.0, ((java.math.BigDecimal) nextDay.get("turnover")).doubleValue());
        assertEquals(0L, ((Number) nextDay.get("placedOrderCount")).longValue());
        for (String key : redis.values.keySet()) {
            assertTrue(key.contains("from:20261118T000000:until:20261119T000000") || key.equals(firstKey));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisOutageFallsBackToTheDatabaseButDatabaseFailuresStillPropagate() {
        RedisTemplate<String, Object> broken = mock(RedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, Object> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(broken.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("test only"));
        ReportServiceImpl degraded = new ReportServiceImpl(mapper, rbac,
                new ReportResultCache(broken, ReportTestSupport.properties(true)), clock);

        assertEquals(170.0, ((java.math.BigDecimal) degraded.business().get("turnover")).doubleValue());

        com.junzhou.mapper.ReportMapper failing = mock(com.junzhou.mapper.ReportMapper.class);
        when(failing.daily(any())).thenThrow(new DataAccessResourceFailureException("database down"));
        ReportServiceImpl unavailable = new ReportServiceImpl(failing, rbac,
                new ReportResultCache(broken, ReportTestSupport.properties(true)), clock);
        assertThrows(DataAccessResourceFailureException.class, unavailable::business);
    }

    @Test
    void unknownMenuKindIsRejectedBeforeTheCacheIsRead() {
        assertThrows(IllegalArgumentException.class, () -> service.menu("category"));
        assertThrows(IllegalArgumentException.class, () -> service.menu(null));
        assertEquals(0, redis.reads.get());
        assertEquals(0, mapper.total());
    }

    @Test
    void disabledCacheQueriesTheDatabaseEveryTime() throws Exception {
        ReportCacheProperties disabled = ReportTestSupport.properties(false);
        ReportServiceImpl uncached = new ReportServiceImpl(mapper, rbac, new ReportResultCache(redis.template, disabled), clock);
        MockMvc uncachedMvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(uncached))
                .addInterceptors(new RbacInterceptor(rbac)).build();

        uncachedMvc.perform(get("/admin/workspace/overviewOrders")).andExpect(status().isOk());
        int first = mapper.calls("overview");
        uncachedMvc.perform(get("/admin/workspace/overviewOrders")).andExpect(status().isOk());
        assertEquals(first + 1, mapper.calls("overview"));
        assertEquals(0, redis.reads.get());
        assertEquals(0, redis.writes.get());
    }
}
