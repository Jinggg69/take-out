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

    @Test void selfServiceNeedsIdentityButNoBusinessStore() throws Exception {
        TenantScopeService scopes=mock(TenantScopeService.class);
        when(scopes.forEmployeeIdentity(42L)).thenReturn(new RequestScope(42L,null,1L,null,java.util.Collections.emptySet()));
        RequestScopeInterceptor interceptor=new RequestScopeInterceptor(scopes);
        BaseContext.setCurrentId(42L);
        for(String method:java.util.Arrays.asList("logout","editPassword")){
            java.lang.reflect.Method m=java.util.Arrays.stream(com.junzhou.controller.admin.EmployeeController.class.getDeclaredMethods()).filter(x->x.getName().equals(method)).findFirst().get();
            org.springframework.web.method.HandlerMethod handler=new org.springframework.web.method.HandlerMethod(new com.junzhou.controller.admin.EmployeeController(),m);
            assertTrue(interceptor.preHandle(new MockHttpServletRequest("POST","/admin/employee/"+method),new MockHttpServletResponse(),handler));
            assertNull(RequestScope.current().getStoreId());
        }
        verify(scopes,never()).forEmployee(anyLong(),any());
    }
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

    @Test void rejectsInactiveOrMissingSelectedStore() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        RequestScopeInterceptor interceptor = new RequestScopeInterceptor(new TenantScopeServiceImpl(mapper));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/dish/list");
        request.addHeader("X-Store-Id", "2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        verify(mapper).activeStoreTenant(2L);
    }

    @Test void acceptsAnActiveSecondStoreWithItsOwnTenant() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        when(mapper.activeStoreTenant(2L)).thenReturn(7L);
        RequestScopeInterceptor interceptor = new RequestScopeInterceptor(new TenantScopeServiceImpl(mapper));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/dish/list");
        request.addHeader("X-Store-Id", "2");
        assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertEquals(7L, RequestScope.requireTenantId());
        assertEquals(2L, RequestScope.requireStoreId());
        verify(mapper,never()).isLegacySingleStore();
    }

    @Test void disabledSavedStoreCanDiscoverOtherStoresButCannotAccessItsMenu() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        when(mapper.storeTenantForDiscovery(2L)).thenReturn(7L);
        RequestScopeInterceptor interceptor = new RequestScopeInterceptor(new TenantScopeServiceImpl(mapper));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/stores");
        request.addHeader("X-Store-Id","2");
        assertTrue(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
        assertEquals(7L,RequestScope.requireTenantId());
        request.setRequestURI("/user/dish/list");
        assertFalse(interceptor.preHandle(request,new MockHttpServletResponse(),new Object()));
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
