package com.junzhou.ratelimit;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@ConfigurationProperties(prefix = "sky.rate-limit")
public class RateLimitProperties implements InitializingBean {
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final long MAX_SAFE_LUA_INTEGER = 9007199254740991L;

    private boolean enabled = true;
    private String namespace = "dev";
    private Rule loginIp = new Rule(60, 60000);
    private Rule loginFailure = new Rule(5, 300000);
    private Rule reminder = new Rule(1, 60000);
    private Rule paymentConfirm = new Rule(1, 2000);
    private Rule reportExport = new Rule(2, 60000);

    @Override
    public void afterPropertiesSet() {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("sky.rate-limit.namespace must match [A-Za-z0-9_-]{1,32}");
        }
        validate("login-ip", loginIp);
        validate("login-failure", loginFailure);
        validate("reminder", reminder);
        validate("payment-confirm", paymentConfirm);
        validate("report-export", reportExport);
    }

    private static void validate(String name, Rule rule) {
        if (rule == null || rule.limit <= 0) {
            throw new IllegalArgumentException("sky.rate-limit." + name + ".limit must be positive");
        }
        if (rule.windowMillis <= 0 || rule.windowMillis > MAX_SAFE_LUA_INTEGER) {
            throw new IllegalArgumentException("sky.rate-limit." + name + ".window-millis is outside the safe range");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public Rule getLoginIp() { return loginIp; }
    public void setLoginIp(Rule loginIp) { this.loginIp = loginIp; }
    public Rule getLoginFailure() { return loginFailure; }
    public void setLoginFailure(Rule loginFailure) { this.loginFailure = loginFailure; }
    public Rule getReminder() { return reminder; }
    public void setReminder(Rule reminder) { this.reminder = reminder; }
    public Rule getPaymentConfirm() { return paymentConfirm; }
    public void setPaymentConfirm(Rule paymentConfirm) { this.paymentConfirm = paymentConfirm; }
    public Rule getReportExport() { return reportExport; }
    public void setReportExport(Rule reportExport) { this.reportExport = reportExport; }

    public static class Rule {
        private int limit;
        private long windowMillis;

        public Rule() {
        }

        public Rule(int limit, long windowMillis) {
            this.limit = limit;
            this.windowMillis = windowMillis;
        }

        public int getLimit() { return limit; }
        public void setLimit(int limit) { this.limit = limit; }
        public long getWindowMillis() { return windowMillis; }
        public void setWindowMillis(long windowMillis) { this.windowMillis = windowMillis; }
    }
}
