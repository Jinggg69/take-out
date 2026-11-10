package com.junzhou.test;
import com.junzhou.security.*;
import com.junzhou.mapper.RbacMapper;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.RbacServiceImpl;
import com.junzhou.context.RequestScope;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class RbacAuthorizationTest {
 @AfterEach void clear(){RequestScope.clear();}
 @Test void diningActionsAreIndependentlyAuthorized() throws Exception {
  RbacService service=mock(RbacService.class);com.junzhou.interceptor.RbacInterceptor interceptor=new com.junzhou.interceptor.RbacInterceptor(service);
  Object controller=new com.junzhou.controller.admin.DiningController(mock(com.junzhou.service.DiningService.class));
  for(java.lang.reflect.Method method:controller.getClass().getDeclaredMethods()) {
   RequireActions required=method.getAnnotation(RequireActions.class);if(required==null)continue;
   for(PermissionAction action:required.value())doThrow(new SecurityException()).when(service).require(action);
   org.springframework.mock.web.MockHttpServletResponse response=new org.springframework.mock.web.MockHttpServletResponse();
   assertFalse(interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest(),response,new org.springframework.web.method.HandlerMethod(controller,method)));
   assertEquals(403,response.getStatus());
  }
 }
 @Test void pickupEndpointsRejectClerksWithoutTheirSpecificAction() throws Exception {
  RbacService service=mock(RbacService.class);
  com.junzhou.interceptor.RbacInterceptor interceptor=new com.junzhou.interceptor.RbacInterceptor(service);
  Object controller=new com.junzhou.controller.admin.OrderController();
  for(String method:Arrays.asList("pickupReady","pickupVerify")) {
   PermissionAction action=method.equals("pickupReady")?PermissionAction.ORDER_PICKUP_READY:PermissionAction.ORDER_PICKUP_VERIFY;
   doThrow(new SecurityException()).when(service).require(action);
   java.lang.reflect.Method m=method.equals("pickupReady")?controller.getClass().getMethod(method,Long.class):controller.getClass().getMethod(method,Long.class,Map.class);
   org.springframework.mock.web.MockHttpServletResponse response=new org.springframework.mock.web.MockHttpServletResponse();
   assertFalse(interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest(),response,new org.springframework.web.method.HandlerMethod(controller,m)));
   assertEquals(403,response.getStatus());verify(service).require(action);
  }
 }
 @Test void actionIsCheckedAgainstItsOwnRoleAndCurrentDatabaseState(){
  RbacMapper m=mock(RbacMapper.class); RbacService s=new RbacServiceImpl(m);
  RequestScope.set(new RequestScope(4L,null,1L,10L,Collections.singleton(10L)));
  when(m.activeEmployee(1L,4L)).thenReturn(1);
  RbacGrantRow a=new RbacGrantRow();a.setStoreId(10L);RbacGrantRow b=new RbacGrantRow();b.setStoreId(11L);
  when(m.activeStoresWithNames(1L)).thenReturn(Arrays.asList(a,b));
  RbacGrantRow grant=new RbacGrantRow();grant.setRoleCode("REFUNDER");grant.setDataScope("SPECIFIED_STORES");grant.setPermissionCode("order:refund");
  when(m.employeeGrants(1L,4L)).thenReturn(Collections.singletonList(grant));
  RbacGrantRow binding=new RbacGrantRow();binding.setRoleCode("REFUNDER");binding.setStoreId(11L);
  when(m.employeeRoleStores(1L,4L)).thenReturn(Collections.singletonList(binding));
  assertThrows(SecurityException.class,()->s.require(PermissionAction.ORDER_REFUND));
  binding.setStoreId(10L);
  assertDoesNotThrow(()->s.require(PermissionAction.ORDER_REFUND));
  when(m.employeeGrants(1L,4L)).thenReturn(Collections.emptyList());
  assertThrows(SecurityException.class,()->s.require(PermissionAction.ORDER_REFUND));
 }
 @Test void lastAdministratorAndRestrictedScopeCannotManageGrants(){
  RbacMapper m=mock(RbacMapper.class);RbacService s=new RbacServiceImpl(m);
  RequestScope.set(new RequestScope(4L,null,1L,10L,Collections.singleton(10L)));
  when(m.lockTenant(1L)).thenReturn(1L);
  when(m.adminCount(1L)).thenReturn(0);
  assertThrows(SecurityException.class,()->s.ensureAdministrator());
  verify(m).lockTenant(1L);
  assertThrows(SecurityException.class,()->s.replacePermissions("CLERK",Arrays.asList("order:refund")));
  verify(m,never()).deletePermissions(anyLong(),anyString());
 }
}
