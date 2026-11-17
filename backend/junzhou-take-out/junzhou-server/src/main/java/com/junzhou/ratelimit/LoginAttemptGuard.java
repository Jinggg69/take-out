package com.junzhou.ratelimit;

import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.vo.EmployeeLoginVO;
import com.junzhou.exception.*;
import org.springframework.stereotype.Component;
import javax.servlet.http.HttpServletRequest;
import java.util.function.Supplier;

@Component
public class LoginAttemptGuard {
    private final RateLimitGuard guard;
    private final ClientIpResolver ips;
    public LoginAttemptGuard(RateLimitGuard guard, ClientIpResolver ips) {
        this.guard = guard;
        this.ips = ips;
    }
    /** Explicitly marks only the platform credential branch; preserves the original response. */
    public static EmployeeLoginVO platform(Supplier<EmployeeLoginVO> authenticate) {
        try { return authenticate.get(); }
        catch (LoginFailedException ex) { throw new PlatformCredentialFailure(ex); }
    }
    public EmployeeLoginVO login(EmployeeLoginDTO input, HttpServletRequest request,
                                 Supplier<EmployeeLoginVO> authenticate) {
        String ip = ips.resolve(request);
        String username = input.getUsername();
        guard.checkLogin(ip, username);
        try { return authenticate.get(); }
        catch (PlatformCredentialFailure ex) {
            guard.recordLoginFailure(ip, username);
            throw ex.original;
        } catch (AccountNotFoundException | PasswordErrorException | AccountLockedException ex) {
            guard.recordLoginFailure(ip, username);
            throw ex;
        }
    }
    private static class PlatformCredentialFailure extends RuntimeException {
        private final LoginFailedException original;
        private PlatformCredentialFailure(LoginFailedException original) { this.original = original; }
    }
}
