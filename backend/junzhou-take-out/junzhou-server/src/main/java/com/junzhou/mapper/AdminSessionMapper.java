package com.junzhou.mapper;

import org.apache.ibatis.annotations.*;

/** Stores session identifiers, never bearer tokens or passwords. */
@Mapper
public interface AdminSessionMapper {
    /** Serialize login issuance with tenant authorization changes, before employee locking. */
    @Select("select id from tenant where id=#{tenantId} and status=1 for update")
    Long lockTenantForIssue(Long tenantId);

    /** Login transaction only. The caller already holds the tenant and credential-verified employee locks. */
    @Update("update employee set password=#{replacement} where id=#{employeeId} and tenant_id=#{tenantId} and status=1 and password=#{verified}")
    int upgradeVerifiedPassword(@Param("employeeId") Long employeeId, @Param("tenantId") Long tenantId,
                                @Param("verified") String verified, @Param("replacement") String replacement);

    /** Authentication bootstrap issuance; tenant is obtained from the credential-locked employee. */
    @Insert("insert into admin_session (session_id,employee_id,expires_at) select #{sessionId},id,#{expiresAt} from employee where id=#{employeeId} and tenant_id=#{tenantId} and status=1")
    int insert(@Param("sessionId") String sessionId, @Param("employeeId") Long employeeId,
               @Param("expiresAt") long expiresAt, @Param("tenantId") Long tenantId);

    /** Pre-context authentication of the exact signed session/employee pair. */
    @Select("select count(*) from admin_session s join employee e on e.id=s.employee_id "
            + "where s.session_id=#{sessionId} and s.employee_id=#{employeeId} "
            + "and s.expires_at>#{now} and e.status=1")
    int countActive(@Param("sessionId") String sessionId, @Param("employeeId") Long employeeId,
                    @Param("now") long now);

    @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireAuthenticatedEmployeeTenantId()'/> delete from admin_session where session_id=#{sessionId} and employee_id=#{employeeId} and exists (select 1 from employee e where e.id=employee_id and e.tenant_id=#{_tenant})</script>")
    int deleteCurrent(@Param("sessionId") String sessionId, @Param("employeeId") Long employeeId);

    @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireAuthenticatedEmployeeTenantId()'/> delete from admin_session where employee_id=#{employeeId} and exists (select 1 from employee e where e.id=employee_id and e.tenant_id=#{_tenant})</script>")
    int deleteByEmployeeId(Long employeeId);

    /** Authentication bootstrap cleanup, tenant comes from the credential-locked employee. */
    @Delete("delete from admin_session where employee_id=#{employeeId} and expires_at<=#{now} and exists (select 1 from employee e where e.id=employee_id and e.tenant_id=#{tenantId})")
    int deleteExpired(@Param("employeeId") Long employeeId, @Param("now") long now, @Param("tenantId") Long tenantId);
}
