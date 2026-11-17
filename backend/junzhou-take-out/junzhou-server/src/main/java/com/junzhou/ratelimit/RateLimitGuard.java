package com.junzhou.ratelimit;

import com.junzhou.context.RequestScope;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Component
public class RateLimitGuard {
    private final RedisRateLimiter limiter;
    private final RateLimitProperties properties;

    public RateLimitGuard(RedisRateLimiter limiter, RateLimitProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    public void checkLogin(String ip, String username) {
        if (!properties.isEnabled()) return;
        RateLimitProperties.Rule ipRule = properties.getLoginIp();
        check(limiter.acquire(prefix() + "login-ip:" + digest(ip), ipRule.getLimit(), ipRule.getWindowMillis()), "login-ip");
        String normalized = validNormalizedUsername(username);
        if (normalized == null) return;
        RateLimitProperties.Rule failures = properties.getLoginFailure();
        check(limiter.checkFailures(loginFailureKey(ip, normalized), failures.getLimit()), "login-failure");
    }

    public void recordLoginFailure(String ip, String username) {
        if (!properties.isEnabled()) return;
        String normalized = validNormalizedUsername(username);
        if (normalized == null) return;
        RateLimitProperties.Rule failures = properties.getLoginFailure();
        limiter.recordFailure(loginFailureKey(ip, normalized), failures.getLimit(), failures.getWindowMillis());
    }

    public void reminder(Long orderId) {
        if (!properties.isEnabled()) return;
        String identity = orderIdentity(orderId);
        RateLimitProperties.Rule rule = properties.getReminder();
        check(limiter.acquire(identity + ":reminder", rule.getLimit(), rule.getWindowMillis()), "order-reminder");
    }

    public void paymentConfirm(Long orderId) {
        if (!properties.isEnabled()) return;
        String identity = orderIdentity(orderId);
        RateLimitProperties.Rule rule = properties.getPaymentConfirm();
        check(limiter.acquire(identity + ":payment-confirm", rule.getLimit(), rule.getWindowMillis()), "payment-confirm");
    }

    public void reportExport() {
        if (!properties.isEnabled()) return;
        Long tenantId = RequestScope.requireEmployeeTenantId();
        Long employeeId = RequestScope.current().getEmployeeId();
        String key = prefix() + "tenant:" + tenantId + ":employee:" + employeeId + ":report-export";
        RateLimitProperties.Rule rule = properties.getReportExport();
        check(limiter.acquire(key, rule.getLimit(), rule.getWindowMillis()), "report-export");
    }

    private String orderIdentity(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("orderId must be positive");
        long tenantId = RequestScope.requireTenantId();
        long storeId = RequestScope.requireStoreId();
        long userId = RequestScope.requireUserId();
        return prefix() + "tenant:" + tenantId + ":store:" + storeId + ":user:" + userId + ":order:" + orderId;
    }

    private void check(RateLimitDecision decision, String ruleId) {
        if (!decision.isAllowed()) {
            throw new RateLimitExceededException(ruleId, decision.getRetryAfterMillis());
        }
    }

    private String loginFailureKey(String ip, String normalizedUsername) {
        return prefix() + "login-failure:" + digest(ip, normalizedUsername);
    }

    private String prefix() {
        return "junzhou:" + properties.getNamespace() + ":rl:v1:";
    }

    private static String validNormalizedUsername(String username) {
        if (username == null || username.length() > 128) return null;
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String digest(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                byte[] bytes = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
