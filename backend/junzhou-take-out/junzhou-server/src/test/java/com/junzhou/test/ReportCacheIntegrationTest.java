package com.junzhou.test;

import com.junzhou.config.RedisConfiguration;
import com.junzhou.context.RequestScope;
import com.junzhou.controller.admin.ReportController;
import com.junzhou.controller.admin.WorkspaceController;
import com.junzhou.interceptor.RbacInterceptor;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.cache.ReportCacheKey;
import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import com.junzhou.test.support.CountingReportMapper;
import com.junzhou.test.support.ReportTestDatabase;
import com.junzhou.test.support.ReportTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * HTTP-level comparison of cache enabled and disabled modes against one database snapshot and a real
 * Redis: identical response bodies, authorization still executed, and fewer aggregate mapper calls.
 */
@SpringBootTest(
        classes = {RedisConfiguration.class, RedisAutoConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ReportCacheIntegrationTest {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ReportTestDatabase db;
    private RbacService rbac;
    private String namespace;
    private final List<String> keys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        db = ReportTestDatabase.create();
        rbac = mock(RbacService.class);
        when(rbac.permits(anyLong(), anyLong(), anyLong(), any(PermissionAction.class))).thenReturn(true);
        namespace = "it" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        RequestScope.set(new RequestScope(1L, null, 1L, 10L, new HashSet<>(Arrays.asList(1L, 2L, 10L))));
    }

    @AfterEach
    void cleanup() {
        RequestScope.clear();
        if (!keys.isEmpty()) redisTemplate.delete(keys);
        keys.clear();
        if (db != null) db.close();
    }

    private ReportCacheProperties cachedProperties() {
        ReportCacheProperties properties = new ReportCacheProperties();
        properties.setNamespace(namespace);
        properties.setWorkspaceTtlSeconds(8L);
        properties.setLiveReportTtlSeconds(8L);
        properties.setHistoricalTtlSeconds(8L);
        properties.setJitterSeconds(0L);
        properties.afterPropertiesSet();
        return properties;
    }

    private static ReportCacheProperties disabledProperties() {
        ReportCacheProperties properties = new ReportCacheProperties();
        properties.setEnabled(false);
        properties.afterPropertiesSet();
        return properties;
    }

    private static MockMvc http(ReportServiceImpl service, RbacService rbac) {
        return MockMvcBuilders.standaloneSetup(new ReportController(service, com.junzhou.test.support.DisabledRateLimitConfiguration.guard()), new WorkspaceController(service))
                .addInterceptors(new RbacInterceptor(rbac)).build();
    }

    private static final String[] REPORT_QUERIES = {
            "/admin/report/turnoverStatistics?begin=2026-11-01&end=2026-11-03&storeIds=1&storeIds=2",
            "/admin/report/ordersStatistics?begin=2026-11-01&end=2026-11-03&storeIds=1&storeIds=2",
            "/admin/report/userStatistics?begin=2026-11-01&end=2026-11-03&storeIds=1&storeIds=2",
            "/admin/report/top10?begin=2026-11-01&end=2026-11-03&storeIds=1&storeIds=2"
    };

    private static final String[] WORKSPACE_QUERIES = {
            "/admin/workspace/businessData",
            "/admin/workspace/overviewOrders",
            "/admin/workspace/overviewDishes",
            "/admin/workspace/overviewSetmeals"
    };

    private String body(MockMvc mvc, String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void cacheEnabledAndDisabledModesReturnIdenticalBodiesWithFewerAggregateQueries() throws Exception {
        CountingReportMapper cachedMapper = new CountingReportMapper(db.mapper());
        CountingReportMapper uncachedMapper = new CountingReportMapper(db.mapper());
        ReportServiceImpl plain = new ReportServiceImpl(uncachedMapper, rbac,
                new ReportResultCache(redisTemplate, disabledProperties()), ReportTestSupport.fixedToday());
        ReportServiceImpl cached = new ReportServiceImpl(cachedMapper, rbac,
                new ReportResultCache(redisTemplate, cachedProperties()), ReportTestSupport.fixedToday());
        MockMvc plainHttp = http(plain, rbac);
        MockMvc cachedHttp = http(cached, rbac);

        for (String url : REPORT_QUERIES) {
            String database = body(plainHttp, url);
            String first = body(cachedHttp, url);
            String second = body(cachedHttp, url);
            assertEquals(database, first, url);
            assertEquals(database, second, url);
        }
        for (String url : WORKSPACE_QUERIES) {
            String database = body(plainHttp, url);
            String first = body(cachedHttp, url);
            String second = body(cachedHttp, url);
            assertEquals(database, first, url);
            assertEquals(database, second, url);
        }

        int uncachedCalls = uncachedMapper.total();
        int cachedCalls = cachedMapper.total();
        assertEquals(12, uncachedCalls, "every uncached request recomputes its aggregates");
        assertEquals(12, cachedCalls, "the warm requests are served from the cache; daily="+cachedMapper.calls("daily")+", overview="+cachedMapper.calls("overview")+", users="+cachedMapper.calls("users")+", sales="+cachedMapper.calls("sales")+", menu="+cachedMapper.calls("menu"));
        assertTrue(cachedCalls < uncachedCalls * 2, "the second pass must not query the report mapper");


        // Authorization keeps running on every request in both modes, including cache hits.
        verify(rbac, atLeast(2 * WORKSPACE_QUERIES.length)).require(PermissionAction.WORKSPACE_READ);
    }

    @Test
    void everyCachedKeyCarriesTheTestNamespaceAndExpires() throws Exception {
        ReportServiceImpl cached = new ReportServiceImpl(new CountingReportMapper(db.mapper()), rbac,
                new ReportResultCache(redisTemplate, cachedProperties()), ReportTestSupport.fixedToday());
        MockMvc cachedHttp = http(cached, rbac);
        for (String url : WORKSPACE_QUERIES) body(cachedHttp, url);

        ReportFilter workspaceDay = new ReportFilter(1L, Collections.singletonList(10L),
                LocalDateTime.of(2026, 11, 17, 0, 0), LocalDateTime.of(2026, 11, 18, 0, 0));
        ReportCacheKey.View[] views = {
                ReportCacheKey.View.WORKSPACE_BUSINESS,
                ReportCacheKey.View.WORKSPACE_ORDERS,
                ReportCacheKey.View.WORKSPACE_DISHES,
                ReportCacheKey.View.WORKSPACE_SETMEALS
        };
        for (ReportCacheKey.View view : views) {
            String key = ReportCacheKey.create(namespace, view, workspaceDay);
            keys.add(key);
            assertNotNull(redisTemplate.opsForValue().get(key), "missing cached view " + view);
            Long ttl = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= 8, "finite ttl for " + view + ": " + ttl);
            assertTrue(key.startsWith("junzhou:" + namespace + ":report-cache:v1:"), key);
        }
    }

    @Test
    void revokedWorkspacePermissionIsDeniedEvenWithWarmCache() throws Exception {
        ReportServiceImpl cached = new ReportServiceImpl(new CountingReportMapper(db.mapper()), rbac,
                new ReportResultCache(redisTemplate, cachedProperties()), ReportTestSupport.fixedToday());
        MockMvc cachedHttp = http(cached, rbac);
        assertEquals(200, cachedHttp.perform(get("/admin/workspace/overviewOrders"))
                .andReturn().getResponse().getStatus());

        doThrow(new SecurityException("revoked")).when(rbac).require(PermissionAction.WORKSPACE_READ);
        assertEquals(403, cachedHttp.perform(get("/admin/workspace/overviewOrders"))
                .andReturn().getResponse().getStatus());
    }
}
