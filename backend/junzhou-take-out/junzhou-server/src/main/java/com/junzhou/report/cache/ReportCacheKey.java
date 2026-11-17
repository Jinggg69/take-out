package com.junzhou.report.cache;

import com.junzhou.report.ReportFilter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Deterministic Redis key for one cached report view.
 *
 * <p>The caller must already have validated the request and authorized every store, so this class
 * only re-checks the structural invariants that keep keys unambiguous. It never consults RBAC and
 * never guesses an employee's permissions.</p>
 */
public final class ReportCacheKey {
    /** The eight cached views; the key segment must stay stable while the schema version does not change. */
    public enum View {
        WORKSPACE_BUSINESS("workspace-business", true),
        WORKSPACE_ORDERS("workspace-orders", true),
        WORKSPACE_DISHES("workspace-dishes", true),
        WORKSPACE_SETMEALS("workspace-setmeals", true),
        REPORT_TURNOVER("report-turnover", false),
        REPORT_ORDERS("report-orders", false),
        REPORT_USERS("report-users", false),
        REPORT_TOP10("report-top10", false);

        private final String segment;
        private final boolean workspace;

        View(String segment, boolean workspace) {
            this.segment = segment;
            this.workspace = workspace;
        }

        /** Stable key segment; never derived from the request URL. */
        public String segment() { return segment; }

        /** Workspace views always use the short workspace TTL. */
        public boolean isWorkspace() { return workspace; }
    }

    private static final String ZONE = "Asia_Shanghai";
    private static final int VERSION = 1;
    private static final int MAX_STORES = 100;
    private static final long MAX_DAYS = 93;
    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final DateTimeFormatter BOUNDARY = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT);

    private ReportCacheKey() { }

    /**
     * Builds the cache key for an already authorized filter.
     *
     * @throws IllegalArgumentException when the namespace or the filter is not in canonical form
     */
    public static String create(String namespace, View view, ReportFilter filter) {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("report-cache namespace must match [A-Za-z0-9_-]{1,32}");
        }
        if (view == null) throw new IllegalArgumentException("report-cache view is required");
        List<Long> stores = canonicalStores(filter);
        return "junzhou:" + namespace + ":report-cache:v" + VERSION
                + ":tenant:" + filter.getTenant()
                + ":view:" + view.segment()
                + ":stores:" + digest(stores)
                + ":from:" + BOUNDARY.format(filter.getFrom())
                + ":until:" + BOUNDARY.format(filter.getUntil())
                + ":zone:" + ZONE;
    }

    /** Natural-day aligned, positive tenant and a bounded sorted store set. */
    private static List<Long> canonicalStores(ReportFilter filter) {
        if (filter == null) throw new IllegalArgumentException("report filter is required");
        Long tenant = filter.getTenant();
        if (tenant == null || tenant <= 0) throw new IllegalArgumentException("report tenant must be positive");
        if (filter.getStores() == null || filter.getStores().isEmpty()) {
            throw new IllegalArgumentException("report stores must not be empty");
        }
        if (filter.getStores().size() > MAX_STORES) throw new IllegalArgumentException("report stores must not exceed 100");
        TreeSet<Long> sorted = new TreeSet<>();
        for (Long id : filter.getStores()) {
            if (id == null || id <= 0) throw new IllegalArgumentException("report store ids must be positive");
            sorted.add(id);
        }
        if (sorted.size() > MAX_STORES) throw new IllegalArgumentException("report stores must not exceed 100");
        if (filter.getFrom() == null || filter.getUntil() == null) {
            throw new IllegalArgumentException("report date bounds are required");
        }
        if (!LocalTime.MIDNIGHT.equals(filter.getFrom().toLocalTime())
                || !LocalTime.MIDNIGHT.equals(filter.getUntil().toLocalTime())) {
            throw new IllegalArgumentException("report date bounds must be natural-day aligned");
        }
        if (!filter.getFrom().isBefore(filter.getUntil())) {
            throw new IllegalArgumentException("report date range must not be empty");
        }
        if (ChronoUnit.DAYS.between(filter.getFrom(), filter.getUntil()) > MAX_DAYS) {
            throw new IllegalArgumentException("report date range must not exceed 93 days");
        }
        return new ArrayList<>(sorted);
    }

    /** Sorted, de-duplicated ids joined by a delimiter so that [1,23] and [12,3] stay distinct. */
    private static String digest(List<Long> stores) {
        StringBuilder joined = new StringBuilder();
        for (Long id : stores) {
            if (joined.length() > 0) joined.append(',');
            joined.append(id);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(joined.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
