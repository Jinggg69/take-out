package com.junzhou.report.cache;

import com.junzhou.report.ReportFilter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Report display cache settings. Invalid values fail startup instead of silently disabling or
 * making the cache permanent.
 */
@Component
@ConfigurationProperties(prefix = "sky.report-cache")
public class ReportCacheProperties implements InitializingBean {
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final long MIN_TTL = 1L;
    private static final long MAX_TTL = 86400L;

    private boolean enabled = true;
    private String namespace = "dev";
    private long workspaceTtlSeconds = 15L;
    private long liveReportTtlSeconds = 15L;
    private long historicalTtlSeconds = 120L;
    private long jitterSeconds = 2L;

    @Override
    public void afterPropertiesSet() {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("sky.report-cache.namespace must match [A-Za-z0-9_-]{1,32}");
        }
        validateTtl("workspace-ttl-seconds", workspaceTtlSeconds);
        validateTtl("live-report-ttl-seconds", liveReportTtlSeconds);
        validateTtl("historical-ttl-seconds", historicalTtlSeconds);
        if (jitterSeconds < 0 || jitterSeconds > MAX_TTL) {
            throw new IllegalArgumentException("sky.report-cache.jitter-seconds must be between 0 and 86400");
        }
    }

    private static void validateTtl(String name, long value) {
        if (value < MIN_TTL || value > MAX_TTL) {
            throw new IllegalArgumentException("sky.report-cache." + name + " must be between 1 and 86400");
        }
    }

    /**
     * Base TTL before jitter. A range ending at or before today's Shanghai midnight is historical;
     * anything else stays short so new events surface quickly.
     */
    public long baseTtlSeconds(ReportCacheKey.View view, ReportFilter filter, LocalDate today) {
        if (view == null) throw new IllegalArgumentException("report-cache view is required");
        if (view.isWorkspace()) return workspaceTtlSeconds;
        if (filter == null || filter.getUntil() == null || today == null) {
            throw new IllegalArgumentException("report filter and captured day are required");
        }
        return filter.getUntil().isAfter(today.atStartOfDay()) ? liveReportTtlSeconds : historicalTtlSeconds;
    }

    /** Applies the documented downward jitter: base - random(0..min(jitter, base-1)), never below 1s. */
    public long jitteredTtlSeconds(long baseTtlSeconds) {
        return jitteredTtlSeconds(baseTtlSeconds, ThreadLocalRandom.current());
    }

    /** Same formula with an explicit random source so tests can assert both range ends. */
    public long jitteredTtlSeconds(long baseTtlSeconds, Random random) {
        long base = Math.max(MIN_TTL, baseTtlSeconds);
        long maximumReduction = Math.min(jitterSeconds, base - 1);
        long reduction = maximumReduction <= 0 ? 0 : random.nextInt((int) maximumReduction + 1);
        return Math.max(MIN_TTL, base - reduction);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public long getWorkspaceTtlSeconds() { return workspaceTtlSeconds; }
    public void setWorkspaceTtlSeconds(long workspaceTtlSeconds) { this.workspaceTtlSeconds = workspaceTtlSeconds; }
    public long getLiveReportTtlSeconds() { return liveReportTtlSeconds; }
    public void setLiveReportTtlSeconds(long liveReportTtlSeconds) { this.liveReportTtlSeconds = liveReportTtlSeconds; }
    public long getHistoricalTtlSeconds() { return historicalTtlSeconds; }
    public void setHistoricalTtlSeconds(long historicalTtlSeconds) { this.historicalTtlSeconds = historicalTtlSeconds; }
    public long getJitterSeconds() { return jitterSeconds; }
    public void setJitterSeconds(long jitterSeconds) { this.jitterSeconds = jitterSeconds; }
}
