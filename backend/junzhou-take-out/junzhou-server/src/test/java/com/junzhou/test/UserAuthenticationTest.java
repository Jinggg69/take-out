package com.junzhou.test;

import com.junzhou.config.WebMvcConfiguration;
import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.properties.JwtProperties;
import com.junzhou.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import com.junzhou.test.support.UserOnlyAdminConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserAuthenticationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private static final String SECRET = "test-user-secret";

    @BeforeEach
    void setUp() {
        BaseContext.removeCurrentId();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class, AuthenticationConfiguration.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        context.close();
    }

    @Test
    void cartRequestReceivesUserIdFromSignedTokenAndClearsItAfterwards() throws Exception {
        mvc.perform(post("/user/shoppingCart/add").header("authentication", token(SECRET, 60000)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("42"));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void cartRequestWithoutTokenIsRejected() throws Exception {
        mvc.perform(post("/user/shoppingCart/add")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidAndExpiredTokensAreRejected() throws Exception {
        mvc.perform(post("/user/shoppingCart/add").header("authentication", token("wrong-secret", 60000)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/user/shoppingCart/add").header("authentication", token(SECRET, -60000)))
                .andExpect(status().isUnauthorized());
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void loginRemainsAccessibleWithoutToken() throws Exception {
        mvc.perform(post("/user/user/login")).andExpect(status().isOk());
    }

    @Test
    void menuRemainsAccessibleWithoutToken() throws Exception {
        for (String path : menuPaths()) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$").value("menu"));
        }
    }

    @Test
    void expiredTokenDoesNotHideMenu() throws Exception {
        String expiredToken = token(SECRET, -60000);
        for (String path : menuPaths()) {
            mvc.perform(get(path).header("authentication", expiredToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$").value("menu"));
        }
    }

    private String[] menuPaths() {
        return new String[]{"/user/category/list", "/user/dish/list", "/user/setmeal/list",
                "/user/setmeal/dish/1", "/user/shop/status"};
    }

    private String token(String secret, long ttl) {
        return JwtUtil.createJWT(secret, ttl, new HashMap<>(Collections.singletonMap(JwtClaimsConstant.USER_ID, 42L)));
    }

    @TestConfiguration
    @ComponentScan("com.junzhou.interceptor")
    @Import(UserOnlyAdminConfiguration.class)
    static class AuthenticationConfiguration {
        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setUserSecretKey(SECRET);
            properties.setUserTokenName("authentication");
            return properties;
        }

        @Bean
        IdentityController identityController() {
            return new IdentityController();
        }
    }

    @RestController
    @TestConfiguration
    static class IdentityController {
        @PostMapping("/user/shoppingCart/add")
        String cart() {
            return String.valueOf(BaseContext.getCurrentId());
        }

        @PostMapping("/user/user/login")
        String login() {
            return "login";
        }

        @GetMapping({"/user/category/list", "/user/dish/list", "/user/setmeal/list",
                "/user/setmeal/dish/{id}", "/user/shop/status"})
        String menu() {
            return "menu";
        }
    }
}
