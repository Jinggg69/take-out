package com.junzhou.test;

import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.interceptor.RequestScopeInterceptor;
import com.junzhou.mapper.TenantScopeMapper;
import com.junzhou.service.TenantScopeService;
import com.junzhou.service.impl.TenantScopeServiceImpl;
import com.junzhou.task.OrderTask;
import com.junzhou.mapper.OrderMaintenanceMapper;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestScopeInterceptorTest {
    @AfterEach void cleanup() { RequestScope.clear(); BaseContext.removeCurrentId(); }

    @Test void validatesSelectionAndCleansAfterRequestAndAsync() throws Exception {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        when(mapper.isLegacySingleStore()).thenReturn(1);
        when(mapper.activeStoreTenant(1L)).thenReturn(1L);
        RequestScopeInterceptor interceptor = new RequestScopeInterceptor(new TenantScopeServiceImpl(mapper));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/dish/list");
        request.addHeader("X-Store-Id", "1");
        BaseContext.setCurrentId(99L); // Public routes cannot inherit a stale identity.
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(RequestScope.current().getUserId());
        assertEquals(1L, RequestScope.current().getTenantId());
        interceptor.afterConcurrentHandlingStarted(request, new MockHttpServletResponse(), new Object());
        assertNull(RequestScope.current());
        assertNull(BaseContext.getCurrentId());
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), new RuntimeException());
        assertNull(RequestScope.current());
        request.removeHeader("X-Store-Id");
        assertFalse(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(RequestScope.current());
    }

    @Test void rejectsAnyAdditionalStoreBeforeUnscopedCodeRuns() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        RequestScopeInterceptor interceptor = new RequestScopeInterceptor(new TenantScopeServiceImpl(mapper));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/dish/list");
        request.addHeader("X-Store-Id", "2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        verify(mapper, never()).activeStoreTenant(anyLong());
    }

    @Test void reusedTaskThreadClearsStaleScopeEvenWhenTaskFails() throws Exception {
        OrderMaintenanceMapper mapper = mock(OrderMaintenanceMapper.class);
        when(mapper.timeoutCandidates(anyLong(), any())).thenAnswer(invocation -> {
            assertNull(RequestScope.current());
            assertTrue(com.junzhou.task.BackgroundJobScope.requireScanning());
            assertNull(BaseContext.getCurrentId());
            RequestScope.set(new RequestScope(null, 2L, 1L, 1L, java.util.Collections.emptySet()));
            throw new IllegalStateException("failure");
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                RequestScope.set(new RequestScope(null, 1L, 1L, 1L, java.util.Collections.emptySet()));
                BaseContext.setCurrentId(1L);
                OrderTask task = new OrderTask(mapper, Clock.systemUTC());
                assertThrows(IllegalStateException.class, task::processTimeoutOrders);
            }).get();
            assertTrue(worker.submit(() -> RequestScope.current() == null && BaseContext.getCurrentId() == null).get());
        } finally { worker.shutdownNow(); }
    }
}
