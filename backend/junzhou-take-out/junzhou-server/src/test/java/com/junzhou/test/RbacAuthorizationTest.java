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
  when(m.activeStores(1L)).thenReturn(Arrays.asList(10L,11L));
  when(m.assignments(1L,4L,"order:refund")).thenReturn(Collections.singletonList(new RbacAssignment("REFUNDER","SPECIFIED_STORES")));
  when(m.roleStores(1L,4L,"REFUNDER")).thenReturn(Collections.singletonList(11L));
  assertThrows(SecurityException.class,()->s.require(PermissionAction.ORDER_REFUND));
  when(m.roleStores(1L,4L,"REFUNDER")).thenReturn(Collections.singletonList(10L));
  assertDoesNotThrow(()->s.require(PermissionAction.ORDER_REFUND));
  when(m.assignments(1L,4L,"order:refund")).thenReturn(Collections.emptyList());
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
