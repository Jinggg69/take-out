package com.junzhou.test;

import com.junzhou.config.WebMvcConfiguration;
import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.interceptor.JwtTokenAdminInterceptor;
import com.junzhou.interceptor.JwtTokenUserInterceptor;
import com.junzhou.interceptor.RequestScopeInterceptor;
import com.junzhou.mapper.TenantScopeMapper;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.AdminSessionService;
import com.junzhou.service.TenantScopeService;
import com.junzhou.service.impl.TenantScopeServiceImpl;
import com.junzhou.utils.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RequestScopeMvcTest {
    AnnotationConfigWebApplicationContext ctx;
    MockMvc mvc;
    TenantScopeMapper mapper;
    @BeforeEach void setup() {
        ctx = new AnnotationConfigWebApplicationContext();
        ctx.setServletContext(new MockServletContext());
        ctx.register(WebMvcConfiguration.class, Config.class);
        ctx.refresh();
        mapper = ctx.getBean(TenantScopeMapper.class);
        when(mapper.isLegacySingleStore()).thenReturn(1);
        when(mapper.activeStoreTenant(1L)).thenReturn(1L);
        when(mapper.activeEmployeeTenant(42L)).thenReturn(1L);
        when(mapper.singleActiveStore(1L)).thenReturn(1L);
        when(ctx.getBean(AdminSessionService.class).isActive("session",42L)).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }
    @AfterEach void teardown() { RequestScope.clear(); BaseContext.removeCurrentId(); ctx.close(); }
    String userToken() { return JwtUtil.createJWT("user-test-secret",60000,new HashMap<>(Collections.singletonMap(JwtClaimsConstant.USER_ID, 42L))); }
    String adminToken() {
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID,42L); claims.put("jti","session");
        return JwtUtil.createJWT("admin-test-secret",60000,claims);
    }
    @Test void authenticatedAndAnonymousRequestsUseValidatedScopeAndCleanIt() throws Exception {
        mvc.perform(get("/user/dish/list").header("X-Store-Id","1").header("X-Tenant-Id","999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("null:1:1"));
        mvc.perform(get("/user/order/probe").header("authentication",userToken()).header("X-Store-Id","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("42:1:1"));
        mvc.perform(get("/admin/probe").header("token",adminToken()).header("X-Store-Id","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("42:1:1"));
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
        RequestScope.set(new RequestScope(null, 99L, 1L, 1L, Collections.emptySet()));
        mvc.perform(get("/user/order/probe").header("authentication","invalid").header("X-Store-Id","1"))
                .andExpect(status().isUnauthorized());
        assertNull(RequestScope.current());
        mvc.perform(get("/user/dish/list")).andExpect(status().isForbidden());
        mvc.perform(get("/user/dish/list").header("X-Store-Id","2")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/probe").header("token",adminToken()).header("X-Store-Id","2"))
                .andExpect(status().isForbidden());
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
        when(mapper.isLegacySingleStore()).thenReturn(0);
        mvc.perform(get("/user/dish/list").header("X-Store-Id","1")).andExpect(status().isForbidden());
    }
    @Configuration static class Config {
        @Bean TenantScopeMapper mapper() { return mock(TenantScopeMapper.class); }
        @Bean TenantScopeService scopes(TenantScopeMapper mapper) { return new TenantScopeServiceImpl(mapper); }
        @Bean RequestScopeInterceptor scopeInterceptor(TenantScopeService service) { return new RequestScopeInterceptor(service); }
        @Bean JwtTokenAdminInterceptor admin() { return new JwtTokenAdminInterceptor(); }
        @Bean JwtTokenUserInterceptor user() { return new JwtTokenUserInterceptor(); }
        @Bean AdminSessionService sessions() { return mock(AdminSessionService.class); }
        @Bean JwtProperties properties() {
            JwtProperties p = new JwtProperties(); p.setAdminSecretKey("admin-test-secret"); p.setAdminTokenName("token");
            p.setUserSecretKey("user-test-secret"); p.setUserTokenName("authentication"); return p;
        }
        @Bean Probe probe() { return new Probe(); }
    }
    @RestController static class Probe {
        @GetMapping({"/admin/probe","/user/order/probe","/user/dish/list"})
        String scope() {
            RequestScope s = RequestScope.current();
            return (s.getEmployeeId() == null ? s.getUserId() : s.getEmployeeId()) + ":" + s.getTenantId() + ":" + s.getStoreId();
        }
    }
}
