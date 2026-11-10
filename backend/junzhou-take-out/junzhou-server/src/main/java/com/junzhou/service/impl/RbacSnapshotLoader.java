package com.junzhou.service.impl;
import com.junzhou.mapper.RbacMapper;
import com.junzhou.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service public class RbacSnapshotLoader {
 private final RbacMapper mapper;
 public RbacSnapshotLoader(RbacMapper mapper){this.mapper=mapper;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public AuthorizationSnapshot load(Long tenant,Long employee){
  Map<Long,String> names=new TreeMap<>();Map<Long,Set<PermissionAction>> actions=new TreeMap<>();Set<PermissionAction> wide=EnumSet.noneOf(PermissionAction.class);
  if(tenant==null||employee==null||mapper.activeEmployee(tenant,employee)!=1)return new AuthorizationSnapshot(tenant,employee,names,actions,wide);
  for(RbacGrantRow s:mapper.activeStoresWithNames(tenant)){names.put(s.getStoreId(),s.getName());actions.put(s.getStoreId(),EnumSet.noneOf(PermissionAction.class));}
  List<RbacGrantRow> grants=mapper.employeeGrants(tenant,employee);
  Map<String,Set<Long>> roleStores=new HashMap<>();
  for(RbacGrantRow row:mapper.employeeRoleStores(tenant,employee))if(row.getStoreId()!=null)roleStores.computeIfAbsent(row.getRoleCode(),k->new HashSet<>()).add(row.getStoreId());
  for(RbacGrantRow row:grants){
   PermissionAction action;DataScope scope;
   try{action=PermissionAction.fromCode(row.getPermissionCode());scope=DataScope.valueOf(row.getDataScope());}catch(RuntimeException invalid){continue;}
   if(scope==DataScope.TENANT_STORES){wide.add(action);for(Set<PermissionAction> a:actions.values())a.add(action);}
   else for(Long id:roleStores.getOrDefault(row.getRoleCode(),Collections.emptySet()))if(actions.containsKey(id))actions.get(id).add(action);
  }
  return new AuthorizationSnapshot(tenant,employee,names,actions,wide);
 }
}
