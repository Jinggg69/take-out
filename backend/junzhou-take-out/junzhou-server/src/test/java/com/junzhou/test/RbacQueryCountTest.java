package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.mapper.RbacMapper;
import com.junzhou.service.impl.RbacServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RbacQueryCountTest {
    @AfterEach void clear() { RequestScope.clear(); }

    @Test void contextLoadsAuthorizationWithoutQueriesPerStoreOrAction() {
        RbacMapper mapper = mock(RbacMapper.class);
        when(mapper.activeEmployee(1L, 2L)).thenReturn(1);
        List<com.junzhou.security.RbacGrantRow> stores=new ArrayList<>();
        for(long id=1;id<=10;id++){com.junzhou.security.RbacGrantRow row=new com.junzhou.security.RbacGrantRow();row.setStoreId(id);stores.add(row);}
        when(mapper.activeStoresWithNames(1L)).thenReturn(stores);
        RequestScope.set(new RequestScope(2L,null,1L,null,Collections.emptySet()));
        clearInvocations(mapper);
        Map<String,Object> result = new RbacServiceImpl(mapper).context();
        assertEquals(Collections.emptyList(), result.get("stores"));
        assertTrue(mockingDetails(mapper).getInvocations().size() <= 4,
                "Authorization query count must not grow with stores and actions");
    }
}
