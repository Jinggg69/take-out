package com.junzhou.mapper;
import com.junzhou.security.RbacAssignment;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface RbacMapper {
 @Select("select id as store_id,name from store where tenant_id=#{t} and status=1")
 @Results({@Result(column="store_id",property="storeId"),@Result(column="name",property="name")})
 List<com.junzhou.security.RbacGrantRow> activeStoresWithNames(Long t);
 @Select("select er.role_code,er.data_scope,rp.permission_code from employee_role er join role_permission rp on rp.tenant_id=er.tenant_id and rp.role_code=er.role_code where er.tenant_id=#{t} and er.employee_id=#{e}")
 @Results({@Result(column="role_code",property="roleCode"),@Result(column="data_scope",property="dataScope"),@Result(column="permission_code",property="permissionCode")})
 List<com.junzhou.security.RbacGrantRow> employeeGrants(@Param("t") Long t,@Param("e") Long e);
 @Select("select role_code,store_id from employee_role_store where tenant_id=#{t} and employee_id=#{e}")
 @Results({@Result(column="role_code",property="roleCode"),@Result(column="store_id",property="storeId")})
 List<com.junzhou.security.RbacGrantRow> employeeRoleStores(@Param("t") Long t,@Param("e") Long e);

 @Select("select name from store where tenant_id=#{t} and id=#{s} and status=1") String storeName(@Param("t") Long t,@Param("s") Long s);
 @Select("select id,name,status from employee where tenant_id=#{t} order by id") java.util.List<java.util.Map<String,Object>> employees(Long t);
 @Select("select code,name from rbac_role where tenant_id=#{t} order by code") java.util.List<java.util.Map<String,Object>> roles(Long t);
 @Select("select code,name from rbac_permission where tenant_id=#{t} order by code") java.util.List<java.util.Map<String,Object>> permissions(Long t);
 @Select("select role_code,permission_code from role_permission where tenant_id=#{t}") java.util.List<java.util.Map<String,Object>> rolePermissions(Long t);
 @Select("select employee_id,role_code,data_scope from employee_role where tenant_id=#{t}") java.util.List<java.util.Map<String,Object>> allAssignments(Long t);
 @Select("select employee_id,role_code,store_id from employee_role_store where tenant_id=#{t}") java.util.List<java.util.Map<String,Object>> allRoleStores(Long t);
 @Select("select id from tenant where id=#{t} and status=1 for update") Long lockTenant(Long t);
 @Select("select id from store where tenant_id=#{t} and status=1") List<Long> activeStores(Long t);
 @Select("select count(*) from employee where tenant_id=#{t} and id=#{e} and status=1") int activeEmployee(@Param("t") Long t,@Param("e") Long e);
 @Select("select er.role_code,er.data_scope from employee_role er join role_permission rp on rp.tenant_id=er.tenant_id and rp.role_code=er.role_code where er.tenant_id=#{t} and er.employee_id=#{e} and rp.permission_code=#{a}")
 List<RbacAssignment> assignments(@Param("t") Long t,@Param("e") Long e,@Param("a") String a);
 @Select("select store_id from employee_store where tenant_id=#{t} and employee_id=#{e}") List<Long> employeeStores(@Param("t") Long t,@Param("e") Long e);
 @Select("select store_id from employee_role_store where tenant_id=#{t} and employee_id=#{e} and role_code=#{r}") List<Long> roleStores(@Param("t") Long t,@Param("e") Long e,@Param("r") String r);
 @Select("select count(distinct e.id) from employee e join employee_role er on er.tenant_id=e.tenant_id and er.employee_id=e.id join role_permission rp on rp.tenant_id=er.tenant_id and rp.role_code=er.role_code where e.tenant_id=#{t} and e.status=1 and er.role_code='MERCHANT_ADMIN' and er.data_scope='TENANT_STORES' and rp.permission_code='employee:manage'") int adminCount(Long t);
 @Select("select count(*) from rbac_role where tenant_id=#{t} and code=#{r}") int roleExists(@Param("t") Long t,@Param("r") String r);
 @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> delete from admin_session where employee_id in (select id from employee where tenant_id=#{_tenant})</script>") int revokeTenant(Long t);
 @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> delete from role_permission where tenant_id=#{_tenant} and role_code=#{r}</script>") int deletePermissions(@Param("t") Long t,@Param("r") String r);
 @Insert("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> insert into role_permission(tenant_id,role_code,permission_code) values(#{_tenant},#{r},#{a})</script>") void addPermission(@Param("t") Long t,@Param("r") String r,@Param("a") String a);
 @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> delete from employee_role_store where tenant_id=#{_tenant} and employee_id=#{e}</script>") void deleteRoleStores(@Param("t") Long t,@Param("e") Long e);
 @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> delete from employee_role where tenant_id=#{_tenant} and employee_id=#{e}</script>") void deleteRoles(@Param("t") Long t,@Param("e") Long e);
 @Delete("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> delete from employee_store where tenant_id=#{_tenant} and employee_id=#{e}</script>") void deleteStores(@Param("t") Long t,@Param("e") Long e);
 @Insert("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> insert into employee_role(tenant_id,employee_id,role_code,data_scope) values(#{_tenant},#{e},#{r},#{s})</script>") void addRole(@Param("t") Long t,@Param("e") Long e,@Param("r") String r,@Param("s") String s);
 @Insert("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> insert into employee_store(tenant_id,employee_id,store_id) values(#{_tenant},#{e},#{s})</script>") void addStore(@Param("t") Long t,@Param("e") Long e,@Param("s") Long s);
 @Insert("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> insert into employee_role_store(tenant_id,employee_id,role_code,store_id) values(#{_tenant},#{e},#{r},#{s})</script>") void addRoleStore(@Param("t") Long t,@Param("e") Long e,@Param("r") String r,@Param("s") Long s);
}
