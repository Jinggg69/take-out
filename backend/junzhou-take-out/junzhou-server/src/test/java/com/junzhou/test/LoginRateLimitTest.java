package com.junzhou.test;

import com.junzhou.controller.admin.EmployeeController;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.exception.AccountLockedException;
import com.junzhou.exception.AccountNotFoundException;
import com.junzhou.exception.LoginFailedException;
import com.junzhou.exception.PasswordErrorException;
import com.junzhou.platform.PlatformAuthService;
import com.junzhou.ratelimit.ClientIpResolver;
import com.junzhou.ratelimit.LoginAttemptGuard;
import com.junzhou.ratelimit.RateLimitExceededException;
import com.junzhou.ratelimit.RateLimitGuard;
import com.junzhou.ratelimit.RateLimitUnavailableException;
import com.junzhou.result.Result;
import com.junzhou.service.EmployeeService;
import com.junzhou.service.LoginCaptchaService;
import com.junzhou.vo.EmployeeLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginRateLimitTest {
    private static final String IP = "192.0.2.10";

    private RateLimitGuard rateLimitGuard;
    private LoginAttemptGuard attemptGuard;
    private EmployeeLoginDTO input;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        rateLimitGuard = mock(RateLimitGuard.class);
        attemptGuard = new LoginAttemptGuard(rateLimitGuard, new ClientIpResolver());
        input = new EmployeeLoginDTO();
        input.setUsername("employee42");
        input.setPassword("secret");
        input.setCaptcha("captcha");
        request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
    }

    @Test
    void fifthFailureKeepsCredentialErrorAndSixthIsRejectedBeforeAuthentication() {
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger authentications = new AtomicInteger();
        RateLimitExceededException exceeded = mock(RateLimitExceededException.class);
        doAnswer(invocation -> {
            if (failures.get() >= 5) throw exceeded;
            return null;
        }).when(rateLimitGuard).checkLogin(IP, input.getUsername());
        doAnswer(invocation -> {
            failures.incrementAndGet();
            return null;
        }).when(rateLimitGuard).recordLoginFailure(IP, input.getUsername());
        Supplier<EmployeeLoginVO> authenticate = () -> {
            authentications.incrementAndGet();
            throw new PasswordErrorException("密码错误");
        };

        for (int i = 0; i < 5; i++) {
            PasswordErrorException failure = assertThrows(PasswordErrorException.class,
                    () -> attemptGuard.login(input, request, authenticate));
            assertEquals("密码错误", failure.getMessage());
        }
        assertSame(exceeded, assertThrows(RateLimitExceededException.class,
                () -> attemptGuard.login(input, request, authenticate)));
        assertEquals(5, authentications.get());
        verify(rateLimitGuard, times(5)).recordLoginFailure(IP, input.getUsername());
        verify(rateLimitGuard, times(6)).checkLogin(IP, input.getUsername());
    }

    @Test
    void everyEmployeeCredentialCategoryIsRecordedExactlyOnce() {
        RuntimeException[] failures = {
                new AccountNotFoundException("账号不存在"),
                new PasswordErrorException("密码错误"),
                new AccountLockedException("账号已锁定")
        };
        for (RuntimeException failure : failures) {
            assertSame(failure, assertThrows(failure.getClass(),
                    () -> attemptGuard.login(input, request, () -> { throw failure; })));
        }
        verify(rateLimitGuard, times(3)).recordLoginFailure(IP, input.getUsername());
    }

    @Test
    void successAndNonCredentialFailuresDoNotRecordOrClearFailureWindow() {
        EmployeeLoginVO expected = EmployeeLoginVO.builder().id(42L).build();
        assertSame(expected, attemptGuard.login(input, request, () -> expected));
        LoginFailedException sessionFailure = new LoginFailedException("账号状态已变化，请重新登录");
        assertSame(sessionFailure, assertThrows(LoginFailedException.class,
                () -> attemptGuard.login(input, request, () -> { throw sessionFailure; })));
        IllegalStateException databaseFailure = new IllegalStateException("database unavailable");
        assertSame(databaseFailure, assertThrows(IllegalStateException.class,
                () -> attemptGuard.login(input, request, () -> { throw databaseFailure; })));
        verify(rateLimitGuard, never()).recordLoginFailure(any(), any());
        verify(rateLimitGuard, times(3)).checkLogin(IP, input.getUsername());
    }

    @Test
    void remoteAddrIsUsedEvenWhenForwardingHeadersAreForgedAndCredentialsStayUnchanged() {
        request.addHeader("X-Forwarded-For", "198.51.100.8");
        request.addHeader("X-Real-IP", "198.51.100.9");
        request.addHeader("Forwarded", "for=198.51.100.10");
        input.setUsername("  Employee42  ");
        EmployeeLoginVO expected = EmployeeLoginVO.builder().id(42L).build();

        assertSame(expected, attemptGuard.login(input, request, () -> {
            assertEquals("  Employee42  ", input.getUsername());
            assertEquals("secret", input.getPassword());
            return expected;
        }));
        verify(rateLimitGuard).checkLogin(IP, "  Employee42  ");
    }

    @Test
    void emptyAndOverlongUsernamesAreDelegatedForIpOnlyProtectionWithoutChangingAuthentication() {
        String overlong = new String(new char[129]).replace('\0', 'a');
        input.setUsername("   ");
        attemptGuard.login(input, request, () -> EmployeeLoginVO.builder().id(1L).build());
        input.setUsername(overlong);
        attemptGuard.login(input, request, () -> EmployeeLoginVO.builder().id(2L).build());

        verify(rateLimitGuard).checkLogin(IP, "   ");
        verify(rateLimitGuard).checkLogin(IP, overlong);
        verify(rateLimitGuard, never()).recordLoginFailure(any(), any());
    }

    @Test
    void limiterReadAndWriteFailuresPropagateWithoutBeingConvertedToCredentialFailures() {
        RateLimitUnavailableException readFailure = mock(RateLimitUnavailableException.class);
        doThrow(readFailure).when(rateLimitGuard).checkLogin(IP, input.getUsername());
        Supplier<EmployeeLoginVO> authenticate = mock(Supplier.class);
        assertSame(readFailure, assertThrows(RateLimitUnavailableException.class,
                () -> attemptGuard.login(input, request, authenticate)));
        verify(authenticate, never()).get();

        reset(rateLimitGuard);
        RateLimitUnavailableException writeFailure = mock(RateLimitUnavailableException.class);
        doThrow(writeFailure).when(rateLimitGuard).recordLoginFailure(IP, input.getUsername());
        assertSame(writeFailure, assertThrows(RateLimitUnavailableException.class,
                () -> attemptGuard.login(input, request, () -> { throw new PasswordErrorException("密码错误"); })));
    }

    @Test
    void captchaFailureDoesNotConsumePasswordFailureOrAttemptAuthentication() {
        EmployeeService employees = mock(EmployeeService.class);
        PlatformAuthService platform = mock(PlatformAuthService.class);
        LoginCaptchaService captcha = mock(LoginCaptchaService.class);
        EmployeeController controller = controller(employees, platform, captcha);
        LoginFailedException invalidCaptcha = new LoginFailedException("验证码错误或已过期，请重新输入");
        doThrow(invalidCaptcha).when(captcha).verify(isNull(), eq("captcha"));

        assertSame(invalidCaptcha, assertThrows(LoginFailedException.class,
                () -> controller.login(input, request)));
        verify(platform, never()).loginIfPlatform(any());
        verify(employees, never()).loginWithSession(any());
        verify(rateLimitGuard).checkLogin(IP, input.getUsername());
        verify(rateLimitGuard, never()).recordLoginFailure(any(), any());
    }

    @Test
    void platformCredentialFailureIsRecordedOnceAndNeverFallsBackToEmployee() {
        EmployeeService employees = mock(EmployeeService.class);
        PlatformAuthService platform = mock(PlatformAuthService.class);
        EmployeeController controller = controller(employees, platform, mock(LoginCaptchaService.class));
        LoginFailedException badPlatformPassword = new LoginFailedException("账号或密码错误，或账号已停用");
        when(platform.loginIfPlatform(input)).thenThrow(badPlatformPassword);

        assertSame(badPlatformPassword, assertThrows(LoginFailedException.class,
                () -> controller.login(input, request)));
        verify(employees, never()).loginWithSession(any());
        verify(rateLimitGuard).recordLoginFailure(IP, input.getUsername());
    }

    @Test
    void platformNullFallsBackToEmployeeWithoutDoubleCounting() {
        EmployeeService employees = mock(EmployeeService.class);
        PlatformAuthService platform = mock(PlatformAuthService.class);
        EmployeeController controller = controller(employees, platform, mock(LoginCaptchaService.class));
        when(platform.loginIfPlatform(input)).thenReturn(null);
        PasswordErrorException badEmployeePassword = new PasswordErrorException("密码错误");
        when(employees.loginWithSession(input)).thenThrow(badEmployeePassword);

        assertSame(badEmployeePassword, assertThrows(PasswordErrorException.class,
                () -> controller.login(input, request)));
        verify(rateLimitGuard, times(1)).recordLoginFailure(IP, input.getUsername());
    }

    @Test
    void employeeSessionIssuanceFailureIsNotRecordedAsCredentialFailure() {
        EmployeeService employees = mock(EmployeeService.class);
        PlatformAuthService platform = mock(PlatformAuthService.class);
        EmployeeController controller = controller(employees, platform, mock(LoginCaptchaService.class));
        when(platform.loginIfPlatform(input)).thenReturn(null);
        LoginFailedException issuanceFailure = new LoginFailedException("账号状态已变化，请重新登录");
        when(employees.loginWithSession(input)).thenThrow(issuanceFailure);

        assertSame(issuanceFailure, assertThrows(LoginFailedException.class,
                () -> controller.login(input, request)));
        verify(rateLimitGuard, never()).recordLoginFailure(any(), any());
    }

    @Test
    void platformFallbackSuccessKeepsOriginalResultAndSessionMechanism() {
        EmployeeService employees = mock(EmployeeService.class);
        PlatformAuthService platform = mock(PlatformAuthService.class);
        EmployeeController controller = controller(employees, platform, mock(LoginCaptchaService.class));
        EmployeeLoginVO issued = EmployeeLoginVO.builder().id(42L).token("persistent-session-token").build();
        when(platform.loginIfPlatform(input)).thenReturn(null);
        when(employees.loginWithSession(input)).thenReturn(issued);

        Result<EmployeeLoginVO> result = controller.login(input, request);
        assertEquals(1, result.getCode());
        assertSame(issued, result.getData());
        verify(employees).loginWithSession(input);
        verify(rateLimitGuard, never()).recordLoginFailure(any(), any());
    }

    private EmployeeController controller(EmployeeService employees, PlatformAuthService platform,
                                          LoginCaptchaService captcha) {
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", employees);
        ReflectionTestUtils.setField(controller, "platformAuth", platform);
        ReflectionTestUtils.setField(controller, "captcha", captcha);
        ReflectionTestUtils.setField(controller, "loginAttemptGuard", attemptGuard);
        return controller;
    }
}
