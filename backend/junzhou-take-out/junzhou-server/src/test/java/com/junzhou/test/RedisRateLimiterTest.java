package com.junzhou.test;

import com.junzhou.config.RedisConfiguration;
import com.junzhou.ratelimit.RateLimitDecision;
import com.junzhou.ratelimit.RateLimitUnavailableException;
import com.junzhou.ratelimit.RedisRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(
        classes = {RedisConfiguration.class, RedisAutoConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class RedisRateLimiterTest {
    @Autowired
    private StringRedisTemplate redis;

    private final List<String> keys = new ArrayList<>();

    private String key(String rule) {
        String value = "junzhou:test:rate-limit:" + rule + ":" + UUID.randomUUID();
        keys.add(value);
        return value;
    }

    @AfterEach
    void cleanUp() {
        if (!keys.isEmpty()) redis.delete(keys);
    }

    @Test
    void realRedisLoginHttpChainRejectsSixthWithoutAuthentication() throws Exception {
        com.junzhou.ratelimit.RateLimitProperties properties = new com.junzhou.ratelimit.RateLimitProperties();
        properties.setNamespace("test" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        com.junzhou.ratelimit.RateLimitGuard guard = new com.junzhou.ratelimit.RateLimitGuard(new RedisRateLimiter(redis), properties);
        com.junzhou.controller.admin.EmployeeController controller = new com.junzhou.controller.admin.EmployeeController();
        com.junzhou.service.EmployeeService employees = mock(com.junzhou.service.EmployeeService.class);
        when(employees.loginWithSession(any())).thenThrow(new com.junzhou.exception.PasswordErrorException("bad password"));
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "employeeService", employees);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "captcha", mock(com.junzhou.service.LoginCaptchaService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "loginAttemptGuard", new com.junzhou.ratelimit.LoginAttemptGuard(guard, new com.junzhou.ratelimit.ClientIpResolver()));
        org.springframework.test.web.servlet.MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new com.junzhou.handler.GlobalExceptionHandler()).build();
        for (int i = 0; i < 6; i++) {
            org.springframework.test.web.servlet.ResultActions result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/employee/login")
                .contentType("application/json").content("{\"username\":\"employee\",\"password\":\"bad\"}"));
            if (i < 5) result.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.msg").value("bad password"));
            else result.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")));
        }
        verify(employees, times(5)).loginWithSession(any());
    }

    @Test
    void fixedWindowRejectsWithoutIncrementingOrRefreshingTtlAndRecovers() throws Exception {
        RedisRateLimiter limiter = new RedisRateLimiter(redis);
        String key = key("fixed-window");

        assertTrue(limiter.acquire(key, 2, 500).isAllowed());
        assertTrue(limiter.acquire(key, 2, 500).isAllowed());
        Long before = redis.getExpire(key, TimeUnit.MILLISECONDS);
        RateLimitDecision rejected = limiter.acquire(key, 2, 500);
        Long after = redis.getExpire(key, TimeUnit.MILLISECONDS);

        assertFalse(rejected.isAllowed());
        assertTrue(rejected.getRetryAfterMillis() > 0);
        assertEquals("2", redis.opsForValue().get(key));
        assertNotNull(before);
        assertNotNull(after);
        assertTrue(after <= before, "A rejection must not extend the fixed window");

        Thread.sleep(Math.max(650, after + 100));
        assertTrue(limiter.acquire(key, 2, 500).isAllowed());
    }

    @Test
    void twoLimiterInstancesShareQuotaAcrossOneHundredConcurrentRequests() throws Exception {
        String key = key("concurrent");
        RedisRateLimiter first = new RedisRateLimiter(redis);
        RedisRateLimiter second = new RedisRateLimiter(redis);
        int limit = 10;
        int requests = 100;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch ready = new CountDownLatch(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(requests);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        try {
        for (int i = 0; i < requests; i++) {
            final RedisRateLimiter limiter = i % 2 == 0 ? first : second;
            pool.execute(() -> {
                ready.countDown();
                try {
                    if (!start.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    if (limiter.acquire(key, limit, 5000).isAllowed()) accepted.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(0, failures.get());
        assertEquals(limit, accepted.get());
        assertEquals(String.valueOf(limit), redis.opsForValue().get(key));
        assertTrue(redis.getExpire(key, TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void loginFailuresAreCappedAndSuccessfulAuthenticationDoesNotClearThem() {
        RedisRateLimiter limiter = new RedisRateLimiter(redis);
        String key = key("login-failure");
        assertTrue(limiter.checkFailures(key, 5).isAllowed());
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.recordFailure(key, 5, 1000).isAllowed());
        }
        assertEquals("5", redis.opsForValue().get(key));
        RateLimitDecision rejected = limiter.checkFailures(key, 5);
        assertFalse(rejected.isAllowed());
        assertTrue(rejected.getRetryAfterMillis() > 0);
        limiter.recordFailure(key, 5, 1000);
        assertEquals("5", redis.opsForValue().get(key));
        assertFalse(limiter.checkFailures(key, 5).isAllowed());
    }

    @Test
    void missingTtlWrongTypeAndMalformedCountAreStorageFailures() {
        RedisRateLimiter limiter = new RedisRateLimiter(redis);
        String noTtl = key("no-ttl");
        redis.opsForValue().set(noTtl, "1");
        assertThrows(RateLimitUnavailableException.class, () -> limiter.acquire(noTtl, 2, 1000));
        assertThrows(RateLimitUnavailableException.class, () -> limiter.checkFailures(noTtl, 5));
        assertThrows(RateLimitUnavailableException.class, () -> limiter.recordFailure(noTtl, 5, 1000));

        String wrongType = key("wrong-type");
        redis.opsForList().rightPush(wrongType, "1");
        assertThrows(RateLimitUnavailableException.class, () -> limiter.acquire(wrongType, 2, 1000));

        String negative = key("negative");
        redis.opsForValue().set(negative, "-1", 5, TimeUnit.SECONDS);
        assertThrows(RateLimitUnavailableException.class, () -> limiter.acquire(negative, 2, 1000));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void nullOrFailedScriptResultsAreStorageFailures() {
        StringRedisTemplate nullResult = mock(StringRedisTemplate.class);
        doReturn(null).when(nullResult).execute(any(RedisScript.class), anyList(), any(Object[].class));
        RedisRateLimiter limiter = new RedisRateLimiter(nullResult);
        assertThrows(RateLimitUnavailableException.class, () -> limiter.acquire("test:null", 1, 1000));

        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        doThrow(new RedisConnectionFailureException("test only")).when(unavailable)
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
        assertThrows(RateLimitUnavailableException.class,
                () -> new RedisRateLimiter(unavailable).checkFailures("test:unavailable", 5));
    }
}
