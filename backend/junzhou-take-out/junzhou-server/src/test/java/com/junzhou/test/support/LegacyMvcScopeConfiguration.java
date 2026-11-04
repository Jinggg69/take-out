package com.junzhou.test.support;

import com.junzhou.interceptor.RequestScopeInterceptor;
import com.junzhou.service.TenantScopeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Older MVC tests exercise their own business/authentication behavior, not scope. */
@Configuration
public class LegacyMvcScopeConfiguration {
    // Business/authentication regressions use separate RBAC integration tests for authorization.
    @Bean @Primary public com.junzhou.interceptor.RbacInterceptor legacyRbacStub(){
        com.junzhou.interceptor.RbacInterceptor interceptor=mock(com.junzhou.interceptor.RbacInterceptor.class);
        when(interceptor.preHandle(any(),any(),any())).thenReturn(true);return interceptor;
    }

    @Bean public TenantScopeService testTenantScopeService() { return mock(TenantScopeService.class); }
    @Bean @Primary public RequestScopeInterceptor legacyScopeStub() throws Exception {
        RequestScopeInterceptor interceptor = mock(RequestScopeInterceptor.class);
        when(interceptor.preHandle(any(), any(), any())).thenAnswer(invocation -> {
            javax.servlet.http.HttpServletRequest request = invocation.getArgument(0);
            Long id = com.junzhou.context.BaseContext.getCurrentId();
            boolean admin = request.getRequestURI().startsWith("/admin/");
            com.junzhou.context.RequestScope.set(new com.junzhou.context.RequestScope(
                    admin ? id : null, admin ? null : id, 1L, 1L, java.util.Collections.singleton(1L)));
            return true;
        });
        doAnswer(invocation -> { com.junzhou.context.RequestScope.clear(); return null; })
                .when(interceptor).afterCompletion(any(), any(), any(), any());
        return interceptor;
    }
}
