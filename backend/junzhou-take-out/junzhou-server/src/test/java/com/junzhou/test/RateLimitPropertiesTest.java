package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.ratelimit.RateLimitDecision;
import com.junzhou.ratelimit.RateLimitGuard;
import com.junzhou.ratelimit.RateLimitProperties;
import com.junzhou.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitPropertiesTest {
    @AfterEach
    void clearScope() {
        RequestScope.clear();
    }

    @Test
    void defaultsMatchDocumentedRulesAndValidate() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.afterPropertiesSet();
        assertTrue(properties.isEnabled());
        assertEquals("dev", properties.getNamespace());
        assertRule(properties.getLoginIp(), 60, 60000);
        assertRule(properties.getLoginFailure(), 5, 300000);
        assertRule(properties.getReminder(), 1, 60000);
        assertRule(properties.getPaymentConfirm(), 1, 2000);
        assertRule(properties.getReportExport(), 2, 60000);
    }

    @Test
    void invalidLimitsWindowsAndNamespacesFailFast() {
        RateLimitProperties badLimit = new RateLimitProperties();
        badLimit.getLoginIp().setLimit(0);
        assertThrows(IllegalArgumentException.class, badLimit::afterPropertiesSet);
        RateLimitProperties badWindow = new RateLimitProperties();
        badWindow.getReminder().setWindowMillis(-1);
        assertThrows(IllegalArgumentException.class, badWindow::afterPropertiesSet);
        for (String namespace : Arrays.asList("", "bad namespace", "bad:namespace", "abcdefghijklmnopqrstuvwxyz1234567")) {
            RateLimitProperties properties = new RateLimitProperties();
            properties.setNamespace(namespace);
            assertThrows(IllegalArgumentException.class, properties::afterPropertiesSet);
        }
    }

    @Test
    void disabledGuardNeverTouchesRedis() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(false);
        RateLimitGuard guard = new RateLimitGuard(limiter, properties);
        guard.checkLogin("127.0.0.1", "employee");
        guard.recordLoginFailure("127.0.0.1", "employee");
        guard.reminder(1L);
        guard.paymentConfirm(1L);
        guard.reportExport();
        verifyNoInteractions(limiter);
    }

    @Test
    void businessKeysUseTrustedIdentityAndIsolateOrderScopes() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.acquire(anyString(), anyInt(), anyLong()))
                .thenReturn(new RateLimitDecision(true, 0));
        RateLimitGuard guard = new RateLimitGuard(limiter, new RateLimitProperties());
        RequestScope.set(new RequestScope(null, 10L, 1L, 2L,
                new HashSet<>(Arrays.asList(2L, 3L))));
        guard.reminder(888L);
        guard.paymentConfirm(888L);
        RequestScope.set(new RequestScope(null, 10L, 1L, 3L,
                new HashSet<>(Collections.singletonList(3L))));
        guard.reminder(888L);
        RequestScope.set(new RequestScope(null, 10L, 2L, 2L,
                Collections.singleton(2L)));
        guard.reminder(888L);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(limiter, times(4)).acquire(keys.capture(), anyInt(), anyLong());
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(2));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(3));
        assertTrue(keys.getAllValues().get(0).contains(":tenant:1:store:2:user:10:order:888:reminder"));
    }

    @Test
    void reportExportQuotaSurvivesStoreSwitchButNotTenantSwitch() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.acquire(anyString(), anyInt(), anyLong()))
                .thenReturn(new RateLimitDecision(true, 0));
        RateLimitGuard guard = new RateLimitGuard(limiter, new RateLimitProperties());
        RequestScope.set(new RequestScope(5L, null, 1L, 2L,
                new HashSet<>(Arrays.asList(2L, 3L))));
        guard.reportExport();
        RequestScope.set(new RequestScope(5L, null, 1L, 3L,
                new HashSet<>(Arrays.asList(2L, 3L))));
        guard.reportExport();
        RequestScope.set(new RequestScope(5L, null, 2L, 3L,
                Collections.singleton(3L)));
        guard.reportExport();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(limiter, times(3)).acquire(keys.capture(), anyInt(), anyLong());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(2));
        assertTrue(keys.getAllValues().get(0).contains(":tenant:1:employee:5:report-export"));
    }

    @Test
    void loginKeysContainOnlyDigestsAndNormalizeOnlyTheKeyInput() {
        RedisRateLimiter limiter = mock(RedisRateLimiter.class);
        when(limiter.acquire(anyString(), anyInt(), anyLong()))
                .thenReturn(new RateLimitDecision(true, 0));
        when(limiter.checkFailures(anyString(), anyInt()))
                .thenReturn(new RateLimitDecision(true, 0));
        RateLimitGuard guard = new RateLimitGuard(limiter, new RateLimitProperties());
        guard.checkLogin("192.0.2.10", "  Employee@Example.com ");
        guard.checkLogin("192.0.2.10", "employee@example.com");
        guard.recordLoginFailure("192.0.2.10", "  Employee@Example.com ");

        ArgumentCaptor<String> failureKeys = ArgumentCaptor.forClass(String.class);
        verify(limiter, times(2)).checkFailures(failureKeys.capture(), eq(5));
        assertEquals(failureKeys.getAllValues().get(0), failureKeys.getAllValues().get(1));
        assertFalse(failureKeys.getAllValues().get(0).contains("Employee"));
        assertFalse(failureKeys.getAllValues().get(0).contains("192.0.2.10"));
        verify(limiter).recordFailure(eq(failureKeys.getAllValues().get(0)), eq(5), eq(300000L));
    }

    private static void assertRule(RateLimitProperties.Rule rule, int limit, long windowMillis) {
        assertEquals(limit, rule.getLimit());
        assertEquals(windowMillis, rule.getWindowMillis());
    }
}
