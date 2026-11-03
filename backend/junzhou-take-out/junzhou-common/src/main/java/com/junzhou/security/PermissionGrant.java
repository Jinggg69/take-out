package com.junzhou.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** One action from one role assignment. Construct only from trusted server-side data.
 * assignedStoreIds: CURRENT_STORE uses employee_store; SPECIFIED_STORES uses
 * employee_role_store filtered to this exact employee-role assignment.
 * Target tenant must be read from the target store, never supplied by a client.
 * Lifecycle/status checks and loading assignments belong to the authorization service.
 */
public final class PermissionGrant {
    private final Long tenantId;
    private final PermissionAction action;
    private final DataScope scope;
    private final Set<Long> assignedStoreIds;

    public PermissionGrant(Long tenantId, PermissionAction action, DataScope scope, Set<Long> assignedStoreIds) {
        if (!positive(tenantId) || action == null || scope == null || assignedStoreIds == null
                || assignedStoreIds.stream().anyMatch(id -> !positive(id)))
            throw new IllegalArgumentException("Invalid permission grant");
        this.tenantId = tenantId;
        this.action = action;
        this.scope = scope;
        this.assignedStoreIds = Collections.unmodifiableSet(new HashSet<>(assignedStoreIds));
    }

    public boolean allows(Long actorTenantId, Long currentStoreId, Long targetTenantId,
                          Long targetStoreId, PermissionAction requestedAction) {
        if (!tenantId.equals(actorTenantId) || !tenantId.equals(targetTenantId)
                || !positive(targetStoreId) || requestedAction != action) return false;
        switch (scope) {
            case CURRENT_STORE:
                return targetStoreId.equals(currentStoreId) && assignedStoreIds.contains(targetStoreId);
            case SPECIFIED_STORES:
                return assignedStoreIds.contains(targetStoreId);
            case TENANT_STORES:
                return true;
            default:
                return false;
        }
    }

    private static boolean positive(Long id) { return id != null && id > 0; }
}
