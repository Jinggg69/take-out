package com.junzhou.test;

import com.junzhou.annotation.MiniappEntry;
import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.controller.user.UserProfileController;
import com.junzhou.entity.User;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.interceptor.JwtTokenUserInterceptor;
import com.junzhou.interceptor.RequestScopeInterceptor;
import com.junzhou.json.JacksonObjectMapper;
import com.junzhou.mapper.UserMapper;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.TenantScopeService;
import com.junzhou.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MiniappEntryScopeTest {
    private MockMvc mvc;
    private UserMapper users;
    private TenantScopeService scopes;
    private static final String SECRET="miniapp-entry-test-secret";
    @BeforeEach void setup() {
        scopes=mock(TenantScopeService.class);
        when(scopes.forUser(any(),any())).thenAnswer(call -> {
            Long user=call.getArgument(0), store=call.getArgument(1);
            if(store==null || store!=11L) throw new SecurityException("store");
            return new RequestScope(null,user,1L,store,Collections.emptySet());
        });
        users=mock(UserMapper.class);
        User user=User.builder().id(42L).name("甲").avatar("avatar").openid("secret-openid").phone("private").build();
        when(users.getProfileById(42L)).thenReturn(user);
        JwtProperties props=new JwtProperties(); props.setUserSecretKey(SECRET); props.setUserTokenName("authentication");
        JwtTokenUserInterceptor auth=new JwtTokenUserInterceptor(); ReflectionTestUtils.setField(auth,"jwtProperties",props);
        mvc=MockMvcBuilders.standaloneSetup(new Probes(),new UserProfileController(users))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonObjectMapper()))
            .addMappedInterceptors(new String[]{"/user/me","/user/me/**","/user/shoppingCart/**"},auth)
            .addInterceptors(new RequestScopeInterceptor(scopes)).build();
    }
    @AfterEach void clean(){ RequestScope.clear(); BaseContext.removeCurrentId(); }
    private String token(){ return JwtUtil.createJWT(SECRET,60000,new HashMap<>(Collections.singletonMap("userId",42L))); }

    @Test void publicDiscoveryNeedsNeitherIdentityNorStoreAndClearsStaleContext() throws Exception {
        BaseContext.setCurrentId(99L); RequestScope.set(new RequestScope(null,99L,2L,22L,Collections.emptySet()));
        mvc.perform(get("/user/merchants").header("X-Store-Id","malformed")).andExpect(status().isOk())
            .andExpect(jsonPath("$.scopeEmpty").value(true));
        verifyNoInteractions(scopes); assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
    }
    @Test void profileRequiresOnlySignedUserAndWhitelistsResponse() throws Exception {
        mvc.perform(get("/user/me").header("authentication",token()).header("X-Store-Id","bad"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value("42"))
            .andExpect(jsonPath("$.data.nickname").value("甲"))
            .andExpect(jsonPath("$.data.openid").doesNotExist()).andExpect(jsonPath("$.data.phone").doesNotExist());
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId()); verifyNoInteractions(scopes);
    }
    @Test void anonymousIdentityAndStoreWritesAreRejectedAsJson() throws Exception {
        mvc.perform(get("/user/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/user/shoppingCart/probe").header("authentication",token()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(0));
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
    }
    @Test void annotationDoesNotMakeUnregisteredMethodsOrUnknownRoutesPublic() throws Exception {
        mvc.perform(post("/user/merchants")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/user/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/user/category/probe")).andExpect(status().isForbidden());
        mvc.perform(get("/user/category/probe").header("X-Store-Id","11"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value(11));
    }
    @Test void identityOnlyAccessorDoesNotWeakenStoreUserGuard() {
        RequestScope.set(new RequestScope(null,42L,null,null,Collections.emptySet()));
        assertEquals(42L,RequestScope.requireAuthenticatedUserId());
        assertThrows(SecurityException.class,RequestScope::requireUserId);
        RequestScope.set(new RequestScope(42L,null,1L,11L,Collections.singleton(11L)));
        assertThrows(SecurityException.class,RequestScope::requireAuthenticatedUserId);
    }
    @RestController static class Probes {
        @MiniappEntry(MiniappEntry.Scope.PUBLIC) @GetMapping("/user/merchants")
        public Map<String,Boolean> merchants(){return Collections.singletonMap("scopeEmpty",RequestScope.current()==null && BaseContext.getCurrentId()==null);}
        @GetMapping("/user/category/probe") public Map<String,Long> menu(){return Collections.singletonMap("storeId",RequestScope.requireStoreId());}
        @PostMapping("/user/shoppingCart/probe") public Long cart(){return RequestScope.requireUserId();}
    }
}
