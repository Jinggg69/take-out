package com.junzhou.security;
import java.util.*;
/** Immutable authorization result for one employee and tenant. */
public final class AuthorizationSnapshot {
 private final Long tenant, employee;
 private final Map<Long,String> stores;
 private final Map<Long,Set<PermissionAction>> permitted;
 private final Set<PermissionAction> tenantWide;
 public AuthorizationSnapshot(Long t,Long e,Map<Long,String> names,Map<Long,Set<PermissionAction>> actions,Set<PermissionAction> wide){
  tenant=t;employee=e;stores=Collections.unmodifiableMap(new TreeMap<>(names));
  Map<Long,Set<PermissionAction>> copy=new TreeMap<>();
  actions.forEach((id,a)->copy.put(id,Collections.unmodifiableSet(new HashSet<>(a))));
  permitted=Collections.unmodifiableMap(copy);tenantWide=Collections.unmodifiableSet(new HashSet<>(wide));
 }
 public boolean matches(Long t,Long e){return Objects.equals(tenant,t)&&Objects.equals(employee,e);}
 public boolean permits(Long store,PermissionAction action){return action!=null&&permitted.getOrDefault(store,Collections.emptySet()).contains(action);}
 public boolean tenantWide(PermissionAction action){return tenantWide.contains(action);}
 public Set<Long> accessibleStores(){Set<Long> ids=new TreeSet<>();permitted.forEach((id,a)->{if(!a.isEmpty())ids.add(id);});return Collections.unmodifiableSet(ids);}
 public String storeName(Long id){return stores.get(id);}
 public static boolean tenantShared(PermissionAction action){String c=action.getCode();return c.startsWith("employee:")||((c.startsWith("dish:")||c.startsWith("category:")||c.startsWith("setmeal:"))&&!c.endsWith(":read"));}
 public boolean effective(Long store,PermissionAction action){return permits(store,action)&&(!tenantShared(action)||tenantWide(action));}
}
