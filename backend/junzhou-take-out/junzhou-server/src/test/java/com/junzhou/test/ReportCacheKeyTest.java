package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.mapper.ReportMapper;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.cache.ReportCacheKey;
import com.junzhou.report.cache.ReportCacheKey.View;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import com.junzhou.test.support.ReportTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Deterministic key encoding: no RBAC guesses, no request-string dependence, no cross-scope collisions. */
class ReportCacheKeyTest {
    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime UNTIL = LocalDateTime.of(2026, 11, 17, 0, 0);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    @AfterEach
    void clear() {
        RequestScope.clear();
    }

    private static ReportFilter filter(long tenant, List<Long> stores) {
        return new ReportFilter(tenant, stores, FROM, UNTIL);
    }

    private static List<Long> stores(Long... ids) {
        return Arrays.asList(ids);
    }

    @Test
    void allEightViewsProduceDistinctKeys() {
        ReportFilter filter = filter(1L, stores(1L));
        Set<String> keys = new LinkedHashSet<>();
        for (View view : View.values()) {
            String key = ReportCacheKey.create("dev", view, filter);
            assertTrue(key.contains(":view:" + view.segment() + ":"), key);
            assertTrue(key.startsWith("junzhou:dev:report-cache:v1:tenant:1:"), key);
            assertTrue(key.endsWith(":zone:Asia_Shanghai"), key);
            keys.add(key);
        }
        assertEquals(8, keys.size());
    }

    @Test
    void tenantNamespaceViewAndDateRangesAreIsolated() {
        ReportFilter base = filter(1L, stores(1L, 2L));
        String key = ReportCacheKey.create("dev", View.REPORT_TURNOVER, base);

        assertNotEquals(key, ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(2L, stores(1L, 2L))));
        assertNotEquals(key, ReportCacheKey.create("prod", View.REPORT_TURNOVER, base));
        assertNotEquals(key, ReportCacheKey.create("dev", View.REPORT_ORDERS, base));
        assertNotEquals(key, ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L, 2L), FROM, LocalDateTime.of(2026, 11, 18, 0, 0))));
        assertNotEquals(key, ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L, 2L), LocalDateTime.of(2026, 11, 2, 0, 0), UNTIL)));
        // Workspace and cross-store report views must never share a final result.
        assertNotEquals(ReportCacheKey.create("dev", View.WORKSPACE_BUSINESS, base),
                ReportCacheKey.create("dev", View.REPORT_TURNOVER, base));
    }

    @Test
    void storeOrderAndDuplicatesDoNotChangeTheCanonicalKey() {
        String canonical = ReportCacheKey.create("dev", View.REPORT_TOP10, filter(1L, stores(1L, 2L)));

        assertEquals(canonical, ReportCacheKey.create("dev", View.REPORT_TOP10, filter(1L, stores(2L, 1L))));
        assertEquals(canonical, ReportCacheKey.create("dev", View.REPORT_TOP10, filter(1L, stores(2L, 1L, 2L))));
        assertNotEquals(ReportCacheKey.create("dev", View.REPORT_TOP10, filter(1L, stores(1L, 23L))),
                ReportCacheKey.create("dev", View.REPORT_TOP10, filter(1L, stores(12L, 3L))));
        String digest = canonical.replaceAll(".*:stores:([0-9a-f]+):from:.*", "$1");
        assertTrue(SHA256.matcher(digest).matches(), digest);
    }

    @Test
    void defaultDateRangeSharesTheKeyWithTheEquivalentExplicitRange() {
        RbacService rbac = mock(RbacService.class);
        when(rbac.permits(anyLong(), anyLong(), anyLong(), any(PermissionAction.class))).thenReturn(true);
        ReportServiceImpl service = new ReportServiceImpl(mock(ReportMapper.class), rbac,
                ReportTestSupport.disabledCache(), ReportTestSupport.fixedToday());
        RequestScope.set(new RequestScope(1L, null, 1L, 1L, new HashSet<>(Collections.singletonList(1L))));

        ReportFilter implicit = service.filter(null, null, null, false);
        // Fixed clock is 2026-11-17: defaults are the previous 30 days up to yesterday.
        ReportFilter explicit = service.filter("2026-10-18", "2026-11-16", null, false);

        assertEquals(LocalDateTime.of(2026, 10, 18, 0, 0), implicit.getFrom());
        assertEquals(LocalDateTime.of(2026, 11, 17, 0, 0), implicit.getUntil());
        assertEquals(ReportCacheKey.create("dev", View.REPORT_TURNOVER, implicit),
                ReportCacheKey.create("dev", View.REPORT_TURNOVER, explicit));
    }

    @Test
    void bothDocumentedBoundariesAreAccepted() {
        // Exactly 93 inclusive days: 2026-01-01 through 2026-04-03.
        ReportFilter widest = new ReportFilter(1L, stores(1L),
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 4, 4, 0, 0));
        assertNotNull(ReportCacheKey.create("dev", View.REPORT_TURNOVER, widest));

        List<Long> hundred = new ArrayList<>();
        for (long id = 1; id <= 100; id++) hundred.add(id);
        assertNotNull(ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(1L, hundred)));
    }

    @Test
    void structurallyInvalidFiltersAndNamespacesCannotProduceAKey() {
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, null));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", null, filter(1L, stores(1L))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create(null, View.REPORT_TURNOVER, filter(1L, stores(1L))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("bad namespace!", View.REPORT_TURNOVER, filter(1L, stores(1L))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("toolongname_1234567890123456789012345", View.REPORT_TURNOVER, filter(1L, stores(1L))));

        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(0L, stores(1L))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, new ReportFilter(null, stores(1L), FROM, UNTIL)));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(1L, Collections.emptyList())));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(1L, stores(1L, null))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(1L, stores(0L))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(1L, stores(-3L))));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L), null, UNTIL)));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L), FROM, null)));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L), FROM, FROM)));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L), UNTIL, FROM)));
        // Not natural-day aligned: a client string must never become a distinct key.
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L), FROM.plusHours(9), UNTIL)));

        List<Long> tooManyDays = new ArrayList<>();
        for (long id = 1; id <= 101; id++) tooManyDays.add(id);
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER, filter(1L, tooManyDays)));
        assertThrows(IllegalArgumentException.class, () -> ReportCacheKey.create("dev", View.REPORT_TURNOVER,
                new ReportFilter(1L, stores(1L), FROM, FROM.plusDays(94))));
    }
}
