package com.junzhou.service.impl;

import com.junzhou.context.RequestScope;
import com.junzhou.mapper.TenantScopeMapper;
import com.junzhou.service.TenantScopeService;
import org.springframework.stereotype.Service;

import java.util.Collections;

/** 租户与门店请求范围服务实现。 */
@Service
public class TenantScopeServiceImpl implements TenantScopeService {

    private final TenantScopeMapper mapper;

    public TenantScopeServiceImpl(TenantScopeMapper mapper) {
        this.mapper = mapper;
    }

    /** 确认当前数据库仍处于允许的单店过渡状态。 */
    @Override
    public void requireLegacySingleStore() {
        if (mapper.isLegacySingleStore() != 1) {
            throw new SecurityException("Single store required until isolation is complete");
        }
    }

    /** 根据员工所属租户与允许门店建立管理端请求范围。 */
    @Override
    public RequestScope forEmployee(Long employeeId, Long selectedStore) {
        Long tenant = mapper.activeEmployeeTenant(employeeId);
        if (tenant == null || tenant <= 0) {
            throw new SecurityException("No active employee tenant");
        }
        Long store = mapper.singleActiveStore(tenant);
        if (store == null || store <= 0 || (selectedStore != null && !store.equals(selectedStore))) {
            throw new SecurityException("Store not authorised");
        }
        return new RequestScope(employeeId, null, tenant, store, Collections.singleton(store));
    }

    /** 根据有效门店建立用户端请求范围。 */
    @Override
    public RequestScope forUser(Long userId, Long selectedStore) {
        if (selectedStore == null || selectedStore <= 0) {
            throw new SecurityException("Store is required");
        }
        Long tenant = mapper.activeStoreTenant(selectedStore);
        if (tenant == null || tenant <= 0) {
            throw new SecurityException("Store is inactive or missing");
        }
        return new RequestScope(null, userId, tenant, selectedStore, Collections.emptySet());
    }
}
