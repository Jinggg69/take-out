package com.junzhou.context;

import java.util.Collections;
import java.util.Set;

/** Immutable identity and validated business scope for the current thread. */
public final class RequestScope {
    private static final ThreadLocal<RequestScope> CURRENT = new ThreadLocal<>();
    private final Long employeeId, userId, tenantId, storeId;
    private final Set<Long> allowedStoreIds;

    public RequestScope(Long employeeId, Long userId, Long tenantId, Long storeId, Set<Long> allowedStoreIds) {
        this.employeeId = employeeId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.allowedStoreIds = Collections.unmodifiableSet(new java.util.HashSet<>(allowedStoreIds));
    }
    public Long getEmployeeId() { return employeeId; }
    public Long getUserId() { return userId; }
    public Long getTenantId() { return tenantId; }
    public Long getStoreId() { return storeId; }
    public Set<Long> getAllowedStoreIds() { return allowedStoreIds; }
    public static RequestScope current() { return CURRENT.get(); }
    public static void set(RequestScope scope) { CURRENT.set(scope); }
    public static void clear() { CURRENT.remove(); }
}
