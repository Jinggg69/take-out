package com.junzhou.context;

import java.util.Collections;
import java.util.Set;

/** Immutable identity and validated business scope for the current thread. */
public final class RequestScope {
    private static final ThreadLocal<RequestScope> CURRENT = new ThreadLocal<>();
    private final Long employeeId, userId, tenantId, storeId;
    private final Set<Long> allowedStoreIds;
    private final boolean system;

    public RequestScope(Long employeeId, Long userId, Long tenantId, Long storeId, Set<Long> allowedStoreIds) {
        this(employeeId, userId, tenantId, storeId, allowedStoreIds, false);
    }

    private RequestScope(Long employeeId, Long userId, Long tenantId, Long storeId, Set<Long> allowedStoreIds, boolean system) {
        this.employeeId = employeeId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.storeId = storeId;
        this.allowedStoreIds = Collections.unmodifiableSet(new java.util.HashSet<>(allowedStoreIds));
        this.system = system;
    }
    public Long getEmployeeId() { return employeeId; }
    public Long getUserId() { return userId; }
    public Long getTenantId() { return tenantId; }
    public Long getStoreId() { return storeId; }
    public Set<Long> getAllowedStoreIds() { return allowedStoreIds; }
    public static RequestScope current() { return CURRENT.get(); }
    public static void set(RequestScope scope) { CURRENT.set(scope); }
    public static void clear() { CURRENT.remove(); }

    /** 仅由受控后台入口在核验业务归属后创建；禁止从 HTTP 参数构造。 */
    public static RequestScope forSystem(Long tenantId, Long storeId) {
        return new RequestScope(null, null, tenantId, storeId, Collections.emptySet(), true);
    }

    /** 订单范围同时校验身份；员工或受控任务可处理范围内订单。 */
    public static Long requireOrderUserId() {
        requireStoreId();
        RequestScope scope = CURRENT.get();
        if (scope.system) return null;
        if (scope.employeeId != null) { requireEmployeeTenantId(); return null; }
        return requireUserId();
    }

    /** 获取已验证租户，缺少范围时拒绝执行数据库操作。 */
    public static Long requireTenantId() {
        RequestScope scope = CURRENT.get();
        if (scope == null || scope.tenantId == null || scope.tenantId <= 0) throw new SecurityException("Missing tenant scope");
        return scope.tenantId;
    }

    /** 获取当前门店，管理员必须具有该门店权限。 */
    public static Long requireStoreId() {
        requireTenantId();
        RequestScope scope = CURRENT.get();
        if (scope.storeId == null || scope.storeId <= 0 || (scope.employeeId != null && !scope.allowedStoreIds.contains(scope.storeId)))
            throw new SecurityException("Missing or unauthorised store scope");
        return scope.storeId;
    }

    /** 获取用户身份，员工身份不能替代顾客身份。 */
    public static Long requireUserId() {
        requireStoreId();
        return requireAuthenticatedUserId();
    }

    /** 用户自身入口专用；不能代替门店业务的 requireUserId。 */
    public static Long requireAuthenticatedUserId() {
        RequestScope scope = CURRENT.get();
        if (scope == null || scope.userId == null || scope.userId <= 0 || scope.employeeId != null || scope.system)
            throw new SecurityException("Missing user scope");
        return scope.userId;
    }

    public static Long requireEmployeeTenantId(){
        Long tenant=requireAuthenticatedEmployeeTenantId();requireStoreId();return tenant;
    }

    /** 身份自服务不依赖业务门店授权。 */
    public static Long requireAuthenticatedEmployeeTenantId() {
        Long tenant = requireTenantId();
        RequestScope scope = CURRENT.get();
        if (scope.employeeId == null || scope.employeeId <= 0 || scope.userId != null)
            throw new SecurityException("Missing employee scope");
        return tenant;
    }
}
