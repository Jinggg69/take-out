package com.junzhou.test;

import com.junzhou.config.RedisConfiguration;
import com.junzhou.config.ReportClockConfiguration;
import com.junzhou.mapper.ReportMapper;
import com.junzhou.report.cache.ReportCacheProperties;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.service.RbacService;
import com.junzhou.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.Clock;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** Production wiring: the service must resolve the cache and the named Shanghai clock from the context. */
@SpringBootTest(
        classes = {RedisConfiguration.class, RedisAutoConfiguration.class, ReportClockConfiguration.class,
                ReportCacheProperties.class, ReportResultCache.class,
                com.junzhou.service.impl.ReportServiceImpl.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ReportCacheWiringTest {
    @MockBean
    private ReportMapper mapper;
    @MockBean
    private RbacService rbac;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportResultCache cache;

    @Autowired
    @Qualifier("reportClock")
    private Clock reportClock;

    @Autowired
    private ReportCacheProperties properties;

    @Test
    void productionConstructorReceivesTheCacheAndTheShanghaiClock() {
        assertNotNull(reportService);
        assertNotNull(cache);
        assertEquals(ZoneId.of("Asia/Shanghai"), reportClock.getZone());
    }

    @Test
    void contextConfigurationPassesStartupValidation() {
        assertNotNull(properties);
        assertTrue(properties.getNamespace().matches("[A-Za-z0-9_-]{1,32}"));
        assertTrue(properties.getWorkspaceTtlSeconds() >= 1L && properties.getWorkspaceTtlSeconds() <= 86400L);
        assertTrue(properties.getLiveReportTtlSeconds() >= 1L && properties.getLiveReportTtlSeconds() <= 86400L);
        assertTrue(properties.getHistoricalTtlSeconds() >= 1L && properties.getHistoricalTtlSeconds() <= 86400L);
        assertTrue(properties.getJitterSeconds() >= 0L && properties.getJitterSeconds() <= 86400L);
    }
}
