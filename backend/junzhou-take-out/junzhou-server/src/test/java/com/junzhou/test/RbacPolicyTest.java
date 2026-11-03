package com.junzhou.test;

import com.junzhou.security.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RbacPolicyTest {
    private Set<Long> stores(Long... ids) { return new HashSet<>(Arrays.asList(ids)); }
    @Test void concreteActionsRejectWildcardsAndUnknownValues() {
        assertEquals(PermissionAction.ORDER_REFUND, PermissionAction.fromCode("order:refund"));
        for (String code : Arrays.asList("*", "order:*", "*:refund", "ORDER:REFUND", "order:unknown", "", null))
            assertThrows(IllegalArgumentException.class, () -> PermissionAction.fromCode(code));
    }
    @Test void currentStoreRequiresAssignmentAndSameTenant() {
        PermissionGrant grant = new PermissionGrant(1L, PermissionAction.ORDER_ACCEPT, DataScope.CURRENT_STORE, stores(10L));
        assertTrue(grant.allows(1L, 10L, 1L, 10L, PermissionAction.ORDER_ACCEPT));
        assertFalse(grant.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_ACCEPT));
        assertFalse(grant.allows(1L, 11L, 1L, 11L, PermissionAction.ORDER_ACCEPT));
        assertFalse(grant.allows(1L, 10L, 2L, 10L, PermissionAction.ORDER_ACCEPT));
        assertFalse(grant.allows(2L, 10L, 2L, 10L, PermissionAction.ORDER_ACCEPT));
    }
    @Test void specifiedStoresNeverMeanAllStoresWhenEmpty() {
        PermissionGrant grant = new PermissionGrant(1L, PermissionAction.ORDER_ACCEPT, DataScope.SPECIFIED_STORES, stores(10L,11L));
        assertTrue(grant.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_ACCEPT));
        assertFalse(grant.allows(1L, 10L, 1L, 12L, PermissionAction.ORDER_ACCEPT));
        assertFalse(new PermissionGrant(1L, PermissionAction.ORDER_ACCEPT, DataScope.SPECIFIED_STORES, stores())
                .allows(1L, 10L, 1L, 10L, PermissionAction.ORDER_ACCEPT));
    }
    @Test void tenantWideGrantIsActionSpecificAndNeverCrossesTenant() {
        PermissionGrant read = new PermissionGrant(1L, PermissionAction.ORDER_READ, DataScope.TENANT_STORES, stores());
        PermissionGrant refund = new PermissionGrant(1L, PermissionAction.ORDER_REFUND, DataScope.CURRENT_STORE, stores(10L));
        assertTrue(read.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_READ));
        assertFalse(read.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_REFUND));
        assertFalse(refund.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_REFUND));
        assertFalse(read.allows(1L, 10L, 2L, 11L, PermissionAction.ORDER_READ));
        assertFalse(read.allows(1L, 10L, 1L, null, PermissionAction.ORDER_READ));
    }
    @Test void specifiedRoleGrantsKeepIndependentStoreLists() {
        PermissionGrant refund = new PermissionGrant(1L, PermissionAction.ORDER_REFUND, DataScope.SPECIFIED_STORES, stores(10L));
        PermissionGrant read = new PermissionGrant(1L, PermissionAction.ORDER_READ, DataScope.SPECIFIED_STORES, stores(11L));
        assertTrue(refund.allows(1L, 10L, 1L, 10L, PermissionAction.ORDER_REFUND));
        assertFalse(refund.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_REFUND));
        assertTrue(read.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_READ));
        assertFalse(read.allows(1L, 10L, 1L, 10L, PermissionAction.ORDER_READ));
    }
    @Test void invalidInputsFailClosedAndStoresAreCopied() {
        Set<Long> ids=stores(10L);
        PermissionGrant grant=new PermissionGrant(1L, PermissionAction.ORDER_READ, DataScope.SPECIFIED_STORES, ids);
        ids.add(11L);
        assertFalse(grant.allows(1L, 10L, 1L, 11L, PermissionAction.ORDER_READ));
        assertFalse(grant.allows(null, 10L, 1L, 10L, PermissionAction.ORDER_READ));
        assertFalse(grant.allows(1L, 10L, 1L, -1L, PermissionAction.ORDER_READ));
        assertThrows(IllegalArgumentException.class, () -> new PermissionGrant(1L, null, DataScope.CURRENT_STORE, ids));
        assertThrows(IllegalArgumentException.class, () -> new PermissionGrant(1L, PermissionAction.ORDER_READ, null, ids));
    }
}
