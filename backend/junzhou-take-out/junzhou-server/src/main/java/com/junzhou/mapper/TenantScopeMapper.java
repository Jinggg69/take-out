package com.junzhou.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TenantScopeMapper {
    @Select("select case when (select count(*) from tenant)=1 and (select count(*) from store)=1 "
            + "and exists(select 1 from tenant where id=1 and status=1) "
            + "and exists(select 1 from store where id=1 and tenant_id=1 and status=1) then 1 else 0 end")
    int isLegacySingleStore();
    @Select("select e.tenant_id from employee e join tenant t on t.id=e.tenant_id and t.status=1 where e.id=#{id} and e.status=1")
    Long activeEmployeeTenant(Long id);

    // Transitional rule: employees can access the sole active store of their tenant.
    // Multiple stores are deliberately ambiguous until assignment is implemented.
    @Select("select case when count(*)=1 then min(id) else null end from store where tenant_id=#{tenantId} and status=1")
    Long singleActiveStore(Long tenantId);

    @Select("select s.tenant_id from store s join tenant t on t.id=s.tenant_id and t.status=1 where s.id=#{id} and s.status=1")
    Long activeStoreTenant(Long id);
    @Select("select s.tenant_id from store s join tenant t on t.id=s.tenant_id and t.status=1 where s.id=#{id}")
    Long storeTenantForDiscovery(Long id);
}
