package com.junzhou.test;

import com.junzhou.report.ReportFilter;
import com.junzhou.report.cache.ReportCacheKey.View;
import com.junzhou.report.cache.ReportCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Startup validation, documented defaults and TTL classification. */
class ReportCachePropertiesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private static ReportCacheProperties defaults() {
        ReportCacheProperties properties = new ReportCacheProperties();
        properties.afterPropertiesSet();
        return properties;
    }

    private static ReportFilter reportUntil(LocalDateTime until) {
        return new ReportFilter(1L, Arrays.asList(1L), LocalDateTime.of(2026, 1, 1, 0, 0), until);
    }

    private static String yamlValue(String key) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private static String placeholderDefault(String raw) {
        int colon = raw.indexOf(':');
        int close = raw.lastIndexOf('}');
        assertTrue(colon > 0 && close > colon, raw);
        return raw.substring(colon + 1, close);
    }

    @Test
    void defaultsMatchTheShippedApplicationYamlExactly() throws Exception {
        ReportCacheProperties properties = defaults();
        assertEquals(Boolean.parseBoolean(placeholderDefault(yamlValue("sky.report-cache.enabled"))), properties.isEnabled());
        assertEquals(placeholderDefault(yamlValue("sky.report-cache.namespace")), properties.getNamespace());
        assertEquals(Long.parseLong(placeholderDefault(yamlValue("sky.report-cache.workspace-ttl-seconds"))),
                properties.getWorkspaceTtlSeconds());
        assertEquals(Long.parseLong(placeholderDefault(yamlValue("sky.report-cache.live-report-ttl-seconds"))),
                properties.getLiveReportTtlSeconds());
        assertEquals(Long.parseLong(placeholderDefault(yamlValue("sky.report-cache.historical-ttl-seconds"))),
                properties.getHistoricalTtlSeconds());
        assertEquals(Long.parseLong(placeholderDefault(yamlValue("sky.report-cache.jitter-seconds"))),
                properties.getJitterSeconds());
        assertTrue(properties.isEnabled());
    }

    @Test
    void shippedYamlBindsIntoThePropertiesBean() throws Exception {
        // Prove the YAML keys and the relaxed-binding field names actually match, not just the defaults.
        org.springframework.core.env.MutablePropertySources sources = new org.springframework.core.env.MutablePropertySources();
        for (PropertySource<?> source : new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))) {
            sources.addLast(source);
        }
        org.springframework.boot.context.properties.bind.Binder binder =
                new org.springframework.boot.context.properties.bind.Binder(
                        org.springframework.boot.context.properties.source.ConfigurationPropertySources.from(sources),
                        new org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver(sources));
        ReportCacheProperties bound = binder.bind("sky.report-cache",
                org.springframework.boot.context.properties.bind.Bindable.of(ReportCacheProperties.class)).get();

        assertEquals(true, bound.isEnabled());
        assertEquals("dev", bound.getNamespace());
        assertEquals(15L, bound.getWorkspaceTtlSeconds());
        assertEquals(15L, bound.getLiveReportTtlSeconds());
        assertEquals(120L, bound.getHistoricalTtlSeconds());
        assertEquals(2L, bound.getJitterSeconds());
        bound.afterPropertiesSet();
    }

    @Test
    void invalidConfigurationFailsStartup() {
        assertThrows(IllegalArgumentException.class, () -> {
            ReportCacheProperties p = new ReportCacheProperties();
            p.setNamespace("");
            p.afterPropertiesSet();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            ReportCacheProperties p = new ReportCacheProperties();
            p.setNamespace("dev prod");
            p.afterPropertiesSet();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            ReportCacheProperties p = new ReportCacheProperties();
            p.setNamespace("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
            p.afterPropertiesSet();
        });
        assertThrows(IllegalArgumentException.class, () -> {
            ReportCacheProperties p = new ReportCacheProperties();
            p.setNamespace(null);
            p.afterPropertiesSet();
        });
        for (long ttl : new long[]{0L, -1L, 86401L}) {
            assertThrows(IllegalArgumentException.class, () -> {
                ReportCacheProperties p = new ReportCacheProperties();
                p.setWorkspaceTtlSeconds(ttl);
                p.afterPropertiesSet();
            });
            assertThrows(IllegalArgumentException.class, () -> {
                ReportCacheProperties p = new ReportCacheProperties();
                p.setLiveReportTtlSeconds(ttl);
                p.afterPropertiesSet();
            });
            assertThrows(IllegalArgumentException.class, () -> {
                ReportCacheProperties p = new ReportCacheProperties();
                p.setHistoricalTtlSeconds(ttl);
                p.afterPropertiesSet();
            });
        }
        for (long jitter : new long[]{-1L, 86401L}) {
            assertThrows(IllegalArgumentException.class, () -> {
                ReportCacheProperties p = new ReportCacheProperties();
                p.setJitterSeconds(jitter);
                p.afterPropertiesSet();
            });
        }
        // The documented boundary values are legal.
        ReportCacheProperties boundary = new ReportCacheProperties();
        boundary.setWorkspaceTtlSeconds(1L);
        boundary.setLiveReportTtlSeconds(86400L);
        boundary.setHistoricalTtlSeconds(1L);
        boundary.setJitterSeconds(0L);
        boundary.afterPropertiesSet();
    }

    @Test
    void yesterdayBoundaryIsHistoricalAndTodayOrFutureIsLive() {
        ReportCacheProperties properties = defaults();

        assertEquals(120L, properties.baseTtlSeconds(View.REPORT_TURNOVER,
                reportUntil(LocalDateTime.of(2026, 11, 16, 0, 0)), TODAY));
        // until == today's Shanghai midnight is still a historical range.
        assertEquals(120L, properties.baseTtlSeconds(View.REPORT_TURNOVER,
                reportUntil(LocalDateTime.of(2026, 11, 17, 0, 0)), TODAY));
        assertEquals(15L, properties.baseTtlSeconds(View.REPORT_TURNOVER,
                reportUntil(LocalDateTime.of(2026, 11, 18, 0, 0)), TODAY));
        assertEquals(15L, properties.baseTtlSeconds(View.REPORT_TOP10,
                reportUntil(LocalDateTime.of(2027, 1, 1, 0, 0)), TODAY));
    }

    @Test
    void workspaceViewsAlwaysUseTheWorkspaceTtl() {
        ReportCacheProperties properties = defaults();
        for (View view : View.values()) {
            if (!view.isWorkspace()) continue;
            assertEquals(15L, properties.baseTtlSeconds(view, reportUntil(LocalDateTime.of(2020, 1, 1, 0, 0)), TODAY));
            assertEquals(15L, properties.baseTtlSeconds(view, reportUntil(LocalDateTime.of(2030, 1, 1, 0, 0)), TODAY));
        }
        assertThrows(IllegalArgumentException.class, () -> properties.baseTtlSeconds(null, reportUntil(LocalDateTime.now()), TODAY));
        assertThrows(IllegalArgumentException.class, () -> properties.baseTtlSeconds(View.REPORT_TURNOVER, null, TODAY));
    }

    @Test
    void jitterNeverExceedsTheBaseAndNeverDropsBelowOneSecond() {
        ReportCacheProperties properties = defaults();
        assertEquals(2L, properties.getJitterSeconds());

        Random random = new Random(20261117L);
        for (int i = 0; i < 500; i++) {
            long shortTtl = properties.jitteredTtlSeconds(15L, random);
            assertTrue(shortTtl >= 13L && shortTtl <= 15L, "short ttl " + shortTtl);
            long historicalTtl = properties.jitteredTtlSeconds(120L, random);
            assertTrue(historicalTtl >= 118L && historicalTtl <= 120L, "historical ttl " + historicalTtl);
        }

        properties.setJitterSeconds(0L);
        assertEquals(15L, properties.jitteredTtlSeconds(15L, random));
        assertEquals(120L, properties.jitteredTtlSeconds(120L, random));

        // Base 1 cannot be reduced and must never become 0 or negative.
        properties.setJitterSeconds(60L);
        for (int i = 0; i < 100; i++) assertEquals(1L, properties.jitteredTtlSeconds(1L, random));
        for (int i = 0; i < 100; i++) assertEquals(1L, properties.jitteredTtlSeconds(0L, random));
        long largeJitter = properties.jitteredTtlSeconds(5L, random);
        assertTrue(largeJitter >= 1L && largeJitter <= 5L, "large jitter " + largeJitter);
    }

    @Test
    void jitterReachesBothEndsOfItsRange() {
        ReportCacheProperties properties = defaults();
        List<Long> observed = new ArrayList<>();
        Random random = new Random(7L);
        for (int i = 0; i < 2000; i++) observed.add(properties.jitteredTtlSeconds(15L, random));
        assertTrue(observed.contains(13L));
        assertTrue(observed.contains(15L));
        assertFalse(observed.contains(12L));
    }
}
