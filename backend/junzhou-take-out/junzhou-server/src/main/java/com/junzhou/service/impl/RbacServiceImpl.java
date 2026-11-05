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
 public RbacServiceImpl(RbacMapper mapper){this.mapper=mapper;}
 @Override
 public boolean permits(Long tenant,Long employee,Long store,PermissionAction action){
  if(mapper.activeEmployee(tenant,employee)!=1 || !mapper.activeStores(tenant).contains(store)) return false;
  for(RbacAssignment row:mapper.assignments(tenant,employee,action.getCode())){
   DataScope scope;
   try{scope=DataScope.valueOf(row.getDataScope());}catch(RuntimeException ex){continue;}
   List<Long> ids=scope==DataScope.TENANT_STORES?Collections.emptyList():mapper.roleStores(tenant,employee,row.getRoleCode());
   if(new PermissionGrant(tenant,action,scope,new HashSet<>(ids)).allows(tenant,store,tenant,store,action))return true;
  }
  return false;
 }
 @Override
 public void require(PermissionAction action){
  Long tenant=RequestScope.requireEmployeeTenantId();RequestScope scope=RequestScope.current();
  if(!permits(tenant,scope.getEmployeeId(),scope.getStoreId(),action))throw new SecurityException("Action denied");
  String code=action.getCode();
  boolean tenantResource=code.startsWith("employee:") || ((code.startsWith("dish:") || code.startsWith("category:") || code.startsWith("setmeal:")) && !code.endsWith(":read"));
  if(tenantResource && mapper.assignments(tenant,scope.getEmployeeId(),code).stream().noneMatch(r->"TENANT_STORES".equals(r.getDataScope())))throw new SecurityException("Tenant-wide action required for shared resource");
 }
 @Override
 public Set<Long> accessibleStores(Long tenant,Long employee){
  Set<Long> result=new HashSet<>();
  for(Long store:mapper.activeStores(tenant))for(PermissionAction action:PermissionAction.values())
   if(permits(tenant,employee,store,action)){result.add(store);break;}
  return result;
 }
 /** Caller must hold this tenant row lock until the whole mutation commits. */
 @Override
 public void lock(){if(mapper.lockTenant(RequestScope.requireEmployeeTenantId())==null)throw new SecurityException("Inactive tenant");}
 @Override
 public void ensureAdministrator(){lock();if(mapper.adminCount(RequestScope.requireEmployeeTenantId())<1)throw new SecurityException("Last merchant administrator required");}
 @Override
 public Map<String,Object> context(){
  Long t=RequestScope.requireAuthenticatedEmployeeTenantId();Long e=RequestScope.current().getEmployeeId();
  List<Map<String,Object>> stores=new ArrayList<>();
  for(Long id:accessibleStores(t,e)){
   Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);row.put("name",mapper.storeName(t,id));
   List<String> actions=new ArrayList<>();
   RequestScope previous=RequestScope.current();
   try{
    RequestScope.set(new RequestScope(e,null,t,id,Collections.singleton(id)));
    for(PermissionAction action:PermissionAction.values())try{require(action);actions.add(action.getCode());}catch(SecurityException denied){/* omit denied actions */}
   }finally{RequestScope.set(previous);}
   row.put("permissions",actions);stores.add(row);
  }
  stores.sort(java.util.Comparator.comparing(row->((Long)row.get("id"))));
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
  Long t=RequestScope.requireEmployeeTenantId();Long e=RequestScope.current().getEmployeeId();
  if(mapper.assignments(t,e,"employee:manage").stream().noneMatch(r->"TENANT_STORES".equals(r.getDataScope())))throw new SecurityException("Tenant-wide manager required");
 }
 @Override
 @Transactional public void replacePermissions(String role,List<String> actions){
  lock();requireTenantManager();Long t=RequestScope.requireEmployeeTenantId();
  if(actions==null || mapper.roleExists(t,role)!=1)throw new SecurityException("Invalid role");
  try{for(String action:actions)PermissionAction.fromCode(action);}catch(IllegalArgumentException ex){throw new SecurityException("Invalid action");}
  mapper.deletePermissions(t,role);
  for(String action:new HashSet<>(actions))mapper.addPermission(t,role,action);
  ensureAdministrator();mapper.revokeTenant(t);
 }
 @Override
 @Transactional public void replaceAssignments(Long employee,List<RoleGrantInput> grants){
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
 }
}
