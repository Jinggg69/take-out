package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.service.TenantScopeService;
import com.junzhou.service.impl.TenantScopeServiceImpl;
import com.junzhou.mapper.TenantScopeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestScopeTest {
    @AfterEach void cleanup() { RequestScope.clear(); }

    @Test void adminScopeComesFromEmployeeAndAllowsOnlyItsSingleStore() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        when(mapper.activeEmployeeTenant(42L)).thenReturn(7L);
        when(mapper.singleActiveStore(7L)).thenReturn(9L);
        TenantScopeService service = new TenantScopeServiceImpl(mapper);
        RequestScope scope = service.forEmployee(42L, 9L);
        assertEquals(42L, scope.getEmployeeId());
        assertEquals(7L, scope.getTenantId());
        assertEquals(9L, scope.getStoreId());
        assertTrue(scope.getAllowedStoreIds().contains(9L));
        assertThrows(UnsupportedOperationException.class, () -> scope.getAllowedStoreIds().add(8L));
        assertThrows(SecurityException.class, () -> service.forEmployee(42L, 10L));
    }

    @Test void ambiguousOrInactiveEmployeeAndInvalidStoreAreRejected() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        TenantScopeService service = new TenantScopeServiceImpl(mapper);
        when(mapper.activeEmployeeTenant(42L)).thenReturn(null);
        when(mapper.singleActiveStore(7L)).thenReturn(null);
        when(mapper.activeStoreTenant(99L)).thenReturn(null);
        assertThrows(SecurityException.class, () -> service.forEmployee(42L, null));
        when(mapper.activeEmployeeTenant(42L)).thenReturn(7L);
        assertThrows(SecurityException.class, () -> service.forEmployee(42L, null));
        assertThrows(SecurityException.class, () -> service.forUser(3L, 99L));
        assertThrows(SecurityException.class, () -> service.forUser(3L, null));
    }

    @Test void userTenantIsDerivedFromActiveStoreAndScopeDoesNotLeak() {
        TenantScopeMapper mapper = mock(TenantScopeMapper.class);
        when(mapper.activeStoreTenant(9L)).thenReturn(7L);
        TenantScopeService service = new TenantScopeServiceImpl(mapper);
        RequestScope scope = service.forUser(3L, 9L);
        assertEquals(3L, scope.getUserId());
        assertEquals(7L, scope.getTenantId());
        RequestScope.set(scope);
        assertSame(scope, RequestScope.current());
        RequestScope.clear();
        assertNull(RequestScope.current());
    }
}
