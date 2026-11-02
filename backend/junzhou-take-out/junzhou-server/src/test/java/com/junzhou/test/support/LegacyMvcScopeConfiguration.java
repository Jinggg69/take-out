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
    @Bean public TenantScopeService testTenantScopeService() { return mock(TenantScopeService.class); }
    @Bean @Primary public RequestScopeInterceptor legacyScopeStub() throws Exception {
        RequestScopeInterceptor interceptor = mock(RequestScopeInterceptor.class);
        when(interceptor.preHandle(any(), any(), any())).thenReturn(true);
        return interceptor;
    }
}
