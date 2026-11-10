package com.junzhou.service.impl;

import com.junzhou.service.RbacService;
import com.junzhou.context.RequestScope;
import com.junzhou.security.*;
import com.junzhou.mapper.RbacMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service public class RbacServiceImpl implements RbacService {
 private final RbacMapper mapper;
 private final RbacSnapshotLoader loader;
 public RbacServiceImpl(RbacMapper mapper){this(mapper,new RbacSnapshotLoader(mapper));}
 @org.springframework.beans.factory.annotation.Autowired
 public RbacServiceImpl(RbacMapper mapper,RbacSnapshotLoader loader){this.mapper=mapper;this.loader=loader;}
 private AuthorizationSnapshot authorization(Long tenant,Long employee){
  AuthorizationSnapshot cached=RbacRequestContext.current();
  if(cached!=null&&cached.matches(tenant,employee))return cached;
  AuthorizationSnapshot fresh=loader.load(tenant,employee);RbacRequestContext.store(fresh);return fresh;
 }
 @Override public boolean permits(Long tenant,Long employee,Long store,PermissionAction action){
  return store!=null&&action!=null&&authorization(tenant,employee).permits(store,action);
 }
 @Override public void require(PermissionAction action){
  Long tenant=RequestScope.requireEmployeeTenantId();RequestScope scope=RequestScope.current();
  AuthorizationSnapshot snapshot=authorization(tenant,scope.getEmployeeId());
  if(!snapshot.permits(scope.getStoreId(),action))throw new SecurityException("Action denied");
  if(AuthorizationSnapshot.tenantShared(action)&&!snapshot.tenantWide(action))throw new SecurityException("Tenant-wide action required for shared resource");
 }
 @Override public Set<Long> accessibleStores(Long tenant,Long employee){return authorization(tenant,employee).accessibleStores();}
 /** Caller must hold this tenant row lock until the whole mutation commits. */
 @Override
 public void lock(){if(mapper.lockTenant(RequestScope.requireEmployeeTenantId())==null)throw new SecurityException("Inactive tenant");RbacRequestContext.invalidateAfterTransaction();}
 @Override
 public void ensureAdministrator(){lock();if(mapper.adminCount(RequestScope.requireEmployeeTenantId())<1)throw new SecurityException("Last merchant administrator required");}
 @Override
 public Map<String,Object> context(){
  Long t=RequestScope.requireAuthenticatedEmployeeTenantId();Long e=RequestScope.current().getEmployeeId();
  AuthorizationSnapshot snapshot=authorization(t,e);
  List<Map<String,Object>> stores=new ArrayList<>();
  for(Long id:snapshot.accessibleStores()){
   Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("name",snapshot.storeName(id));
   List<String> actions=new ArrayList<>();
   for(PermissionAction action:PermissionAction.values())if(snapshot.effective(id,action))actions.add(action.getCode());
   row.put("permissions",actions);stores.add(row);
  }
  stringifyIds(stores);
  Map<String,Object> result=new LinkedHashMap<>();result.put("stores",stores);return result;
 }
 private void stringifyIds(List<Map<String,Object>> rows){
  for(Map<String,Object> row:rows)for(Map.Entry<String,Object> entry:row.entrySet()){
   String key=entry.getKey().toLowerCase(java.util.Locale.ROOT);
   if((key.equals("id") || key.endsWith("_id")) && entry.getValue()!=null)entry.setValue(String.valueOf(entry.getValue()));
  }
 }
 @Override
 public Map<String,Object> configuration(){
  requireTenantManager();Long t=RequestScope.requireEmployeeTenantId();Map<String,Object> result=new LinkedHashMap<>();
  result.put("employees",mapper.employees(t));result.put("roles",mapper.roles(t));result.put("permissions",mapper.permissions(t));
  result.put("rolePermissions",mapper.rolePermissions(t));result.put("assignments",mapper.allAssignments(t));result.put("roleStores",mapper.allRoleStores(t));
  List<Map<String,Object>> stores=new ArrayList<>();for(Long id:mapper.activeStores(t)){Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("name",mapper.storeName(t,id));stores.add(row);}result.put("stores",stores);
  for(Object rows:result.values()){
   @SuppressWarnings("unchecked") List<Map<String,Object>> typed=(List<Map<String,Object>>)rows;
   stringifyIds(typed);
  }
  return result;
 }
 private void requireTenantManager(){
  require(PermissionAction.EMPLOYEE_MANAGE);

 }
 @Override
 @Transactional public void replacePermissions(String role,List<String> actions){
  try{
  lock();requireTenantManager();Long t=RequestScope.requireEmployeeTenantId();
  if(actions==null || mapper.roleExists(t,role)!=1)throw new SecurityException("Invalid role");
  try{for(String action:actions)PermissionAction.fromCode(action);}catch(IllegalArgumentException ex){throw new SecurityException("Invalid action");}
  mapper.deletePermissions(t,role);
  for(String action:new HashSet<>(actions))mapper.addPermission(t,role,action);
  ensureAdministrator();mapper.revokeTenant(t);
  }finally{RbacRequestContext.invalidateAfterTransaction();}
 }
 @Override
 @Transactional public void replaceAssignments(Long employee,List<RoleGrantInput> grants){
  try{
  lock();requireTenantManager();Long t=RequestScope.requireEmployeeTenantId();
  if(grants==null || mapper.activeEmployee(t,employee)!=1)throw new SecurityException("Invalid employee");
  Set<Long> stores=new HashSet<>();Set<String> roles=new HashSet<>();
  for(RoleGrantInput g:grants){
   if(g==null || g.getDataScope()==null || g.getStoreIds()==null || !roles.add(g.getRoleCode()) || mapper.roleExists(t,g.getRoleCode())!=1)throw new SecurityException("Invalid assignment");
   stores.addAll(g.getStoreIds());
  }
  if(!mapper.activeStores(t).containsAll(stores))throw new SecurityException("Invalid store");
  mapper.deleteRoleStores(t,employee);mapper.deleteRoles(t,employee);mapper.deleteStores(t,employee);
  for(Long store:stores)mapper.addStore(t,employee,store);
  for(RoleGrantInput g:grants){mapper.addRole(t,employee,g.getRoleCode(),g.getDataScope().name());
   if(g.getDataScope()!=DataScope.TENANT_STORES)for(Long store:g.getStoreIds())mapper.addRoleStore(t,employee,g.getRoleCode(),store);
  }
  ensureAdministrator();mapper.revokeTenant(t);
  }finally{RbacRequestContext.invalidateAfterTransaction();}
 }
}
