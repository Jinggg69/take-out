package com.junzhou.test;
import com.junzhou.context.RequestScope;
import com.junzhou.mapper.*;
import com.junzhou.security.*;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.RbacServiceImpl;
import com.junzhou.service.impl.EmployeeServiceImpl;
import com.junzhou.interceptor.RbacInterceptor;
import com.junzhou.controller.admin.OrderController;
import org.junit.jupiter.api.*;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.apache.ibatis.session.*;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RbacIntegrationTest {
 SqlCounter counter=new SqlCounter();
 TenantScopeMapper tenantMapper;
 JdbcTemplate db;RbacService rbac;DataSourceTransactionManager tx;RbacMapper mapper;
 @SuppressWarnings("unchecked") <T> T transactional(T target){
  TransactionInterceptor interceptor=new TransactionInterceptor();interceptor.setTransactionManager(tx);
  Properties props=new Properties();props.setProperty("*","PROPAGATION_REQUIRED");interceptor.setTransactionAttributes(props);
  ProxyFactory proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(interceptor);return (T)proxy.getProxy();
 }
 @BeforeEach void setup(){
  JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
  db=new JdbcTemplate(ds);tx=new DataSourceTransactionManager(ds);
  db.execute("create table tenant(id bigint primary key,status int)");
  db.execute("create table store(id bigint primary key,tenant_id bigint,status int,name varchar(100))");
  db.execute("create table employee(id bigint primary key,tenant_id bigint,status int,name varchar(100))");
  db.execute("create table rbac_role(tenant_id bigint,code varchar(64),name varchar(100))");
  db.execute("create table employee_role(tenant_id bigint,employee_id bigint,role_code varchar(64),data_scope varchar(32))");
  db.execute("create table rbac_permission(tenant_id bigint,code varchar(64),name varchar(100))");
  db.execute("create table role_permission(tenant_id bigint,role_code varchar(64),permission_code varchar(64))");
  db.execute("create table employee_store(tenant_id bigint,employee_id bigint,store_id bigint)");
  db.execute("create table employee_role_store(tenant_id bigint,employee_id bigint,role_code varchar(64),store_id bigint)");
  db.execute("create table admin_session(session_id varchar(64),employee_id bigint,expires_at bigint)");
  db.update("insert into tenant values(1,1),(2,1)");db.update("insert into store values(10,1,1,'A'),(11,1,1,'B'),(20,2,1,'C')");
  db.update("insert into employee values(1,1,1,'admin'),(2,1,1,'clerk'),(3,2,1,'foreign')");
  db.update("insert into rbac_role values(1,'MERCHANT_ADMIN','admin'),(1,'CLERK','clerk'),(2,'MERCHANT_ADMIN','foreign')");
  db.update("insert into employee_role values(1,1,'MERCHANT_ADMIN','TENANT_STORES'),(1,2,'CLERK','SPECIFIED_STORES')");
  db.update("insert into role_permission values(1,'MERCHANT_ADMIN','employee:manage'),(1,'CLERK','order:read'),(1,'CLERK','order:accept')");
  db.update("insert into employee_store values(1,2,10),(1,2,11)");db.update("insert into employee_role_store values(1,2,'CLERK',10)");
  db.update("insert into admin_session values('a',1,9999999999999),('b',2,9999999999999),('c',3,9999999999999)");
  Configuration conf=new Configuration(new Environment("test",new SpringManagedTransactionFactory(),ds));conf.setMapUnderscoreToCamelCase(true);conf.addMapper(RbacMapper.class);conf.addMapper(TenantScopeMapper.class);conf.setLocalCacheScope(LocalCacheScope.STATEMENT);conf.addInterceptor(counter);
  SqlSessionTemplate template=new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(conf));
  mapper=template.getMapper(RbacMapper.class);tenantMapper=template.getMapper(TenantScopeMapper.class);
  rbac=transactional(new RbacServiceImpl(mapper));scope(1L,10L);
 }
 void scope(Long employee,Long store){RequestScope.set(new RequestScope(employee,null,1L,store,Collections.singleton(store)));}
 @AfterEach void clear(){RequestScope.clear();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
 @Test void largeIdentifiersAreSerializedAsExactStrings() throws Exception {
  db.update("insert into store values(9007199254740993,1,1,'large')");
  String json=new com.junzhou.json.JacksonObjectMapper().writeValueAsString(rbac.context());
  assertTrue(json.contains("\"id\":\"9007199254740993\""),json);
 }
 @Test void configurationRequiresTenantManagerAndNeverIncludesForeignEmployees(){
  java.util.Map<String,Object> data=rbac.configuration();
  assertEquals(2,((java.util.List<?>)data.get("employees")).size());
  assertFalse(data.toString().contains("foreign"));assertFalse(data.toString().contains("password"));
  scope(2L,10L);assertThrows(SecurityException.class,()->rbac.configuration());
 }
 @Test void contextOnlyReturnsOwnAllowedStoresAndActions(){
  scope(2L,10L);
  java.util.Map<String,Object> context=rbac.context();
  @SuppressWarnings("unchecked") java.util.List<java.util.Map<String,Object>> stores=(java.util.List<java.util.Map<String,Object>>)context.get("stores");
  assertEquals(1,stores.size());assertEquals("10",stores.get(0).get("id"));
  @SuppressWarnings("unchecked") java.util.List<String> actions=(java.util.List<String>)stores.get(0).get("permissions");
  assertTrue(actions.contains("order:read"));assertFalse(actions.contains("order:refund"));
 }
 @Test void everyAdminHandlerHasExplicitPolicy() throws Exception {
  String[] names={"Category","Common","Dish","Employee","Order","Setmeal","Shop","Rbac"};
  for(String name:names)for(java.lang.reflect.Method m:Class.forName("com.junzhou.controller.admin."+name+"Controller").getDeclaredMethods()){
   if(!org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation(m,org.springframework.web.bind.annotation.RequestMapping.class) || m.getName().equals("login"))continue;
   RequireActions annotation=m.getAnnotation(RequireActions.class);assertNotNull(annotation,m.toString());
   if(annotation.value().length==0)assertTrue((name.equals("Employee") && Arrays.asList("logout","editPassword").contains(m.getName())) || (name.equals("Rbac") && m.getName().equals("context")),m.toString());
  }
 }
 @Test void currentStoreCannotBorrowAnotherRolesAssignment(){
  db.update("update employee_role set data_scope='CURRENT_STORE' where employee_id=2");
  assertFalse(rbac.permits(1L,2L,11L,PermissionAction.ORDER_ACCEPT));
 }
 @Test void assignmentsAreStoreAndActionSpecificAndReadFresh(){
  assertTrue(rbac.permits(1L,2L,10L,PermissionAction.ORDER_ACCEPT));
  assertFalse(rbac.permits(1L,2L,11L,PermissionAction.ORDER_ACCEPT));
  assertFalse(rbac.permits(1L,2L,20L,PermissionAction.ORDER_ACCEPT));
  assertFalse(rbac.permits(1L,2L,10L,PermissionAction.ORDER_REFUND));
  assertFalse(rbac.permits(1L,2L,10L,PermissionAction.REPORT_EXPORT));
  db.update("delete from role_permission where role_code='CLERK'");
  assertFalse(rbac.permits(1L,2L,10L,PermissionAction.ORDER_ACCEPT));
 }
 @Test void permissionChangesRevokeOnlyThisTenantsSessionsAndLastAdminRollback(){
  rbac.replacePermissions("CLERK",Collections.singletonList("order:read"));
  assertEquals(1,db.queryForObject("select count(*) from admin_session",Integer.class));
  assertThrows(SecurityException.class,()->rbac.replacePermissions("MERCHANT_ADMIN",Collections.emptyList()));
  assertEquals(1,db.queryForObject("select count(*) from role_permission where role_code='MERCHANT_ADMIN'",Integer.class));
 }
 @Test void cannotRemoveLastAdminOrGrantForeignStoresAndDeniedWritesRollBack(){
  assertThrows(SecurityException.class,()->rbac.replaceAssignments(1L,Collections.emptyList()));
  assertEquals(1,mapper.adminCount(1L));assertEquals(3,db.queryForObject("select count(*) from admin_session",Integer.class));
  RoleGrantInput grant=new RoleGrantInput();grant.setRoleCode("CLERK");grant.setDataScope(DataScope.SPECIFIED_STORES);grant.setStoreIds(Collections.singleton(20L));
  assertThrows(SecurityException.class,()->rbac.replaceAssignments(2L,Collections.singletonList(grant)));
  assertEquals(Collections.singletonList(10L),mapper.roleStores(1L,2L,"CLERK"));
 }
 @Test void clerkDirectHttpRefundIsForbiddenAndMissingAnnotationDenies() throws Exception {
  scope(2L,10L);OrderController orders=new OrderController();
  org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(orders).addInterceptors(new RbacInterceptor(rbac)).build();
  mvc.perform(post("/admin/order/1/refund/retry")).andExpect(status().isForbidden());
  mvc.perform(put("/admin/order/cancel").contentType("application/json").content("{\"id\":1}")).andExpect(status().isForbidden());
  RbacInterceptor interceptor=new RbacInterceptor(rbac);
  assertFalse(interceptor.preHandle(new org.springframework.mock.web.MockHttpServletRequest(),new org.springframework.mock.web.MockHttpServletResponse(),new org.springframework.web.method.HandlerMethod(this,getClass().getDeclaredMethod("clear"))));
 }
 @Test void concurrentAdministratorRemovalLeavesOneAdministrator() throws Exception {
  db.update("delete from employee_role where employee_id=2");
  db.update("insert into employee_role values(1,2,'MERCHANT_ADMIN','TENANT_STORES')");
  java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
  java.util.List<java.util.concurrent.Future<Boolean>> results=new ArrayList<>();
  try{
   for(Long actor:Arrays.asList(1L,2L))results.add(pool.submit(()->{scope(actor,10L);start.await();try{rbac.replaceAssignments(actor,Collections.emptyList());return true;}catch(SecurityException ex){return false;}finally{RequestScope.clear();}}));
   start.countDown();int succeeded=0;for(java.util.concurrent.Future<Boolean> r:results)if(r.get(10,java.util.concurrent.TimeUnit.SECONDS))succeeded++;
   assertEquals(1,succeeded);assertEquals(1,mapper.adminCount(1L));
  }finally{pool.shutdownNow();}
 }
 @Test void disablingLastAdminRollsBackEmployeeAndKeepsSession(){
  db.update("insert into role_permission values(1,'MERCHANT_ADMIN','employee:status')");
  // Use a real transactional update through a mock only for employee SQL execution;
  // the guard and session transaction remain real.
  EmployeeMapper employees=mock(EmployeeMapper.class);when(employees.update(any())).thenAnswer(i->{com.junzhou.entity.Employee e=i.getArgument(0);return db.update("update employee set status=? where tenant_id=1 and id=?",e.getStatus(),e.getId());});
  EmployeeServiceImpl impl=new EmployeeServiceImpl();ReflectionTestUtils.setField(impl,"employeeMapper",employees);ReflectionTestUtils.setField(impl,"rbac",rbac);ReflectionTestUtils.setField(impl,"adminSessionMapper",mock(AdminSessionMapper.class));
  com.junzhou.service.EmployeeService service=transactional(impl);
  assertThrows(SecurityException.class,()->service.startOrStop(0,1L));
  assertEquals(1,db.queryForObject("select status from employee where id=1",Integer.class));
 }

 @org.apache.ibatis.plugin.Intercepts(@org.apache.ibatis.plugin.Signature(type=org.apache.ibatis.executor.statement.StatementHandler.class,method="query",args={java.sql.Statement.class,org.apache.ibatis.session.ResultHandler.class}))
 public static class SqlCounter implements org.apache.ibatis.plugin.Interceptor {
  int count;
  public Object intercept(org.apache.ibatis.plugin.Invocation i)throws Throwable{count++;return i.proceed();}
 }
 private org.springframework.mock.web.MockHttpServletRequest request(){
  org.springframework.mock.web.MockHttpServletRequest r=new org.springframework.mock.web.MockHttpServletRequest();
  org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(r));
  RbacRequestContext.begin(r);return r;
 }
 @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={1,3,10})
 void authorizationQueriesAreConstantAndReused(int stores){
  db.update("delete from store where tenant_id=1");
  for(int i=0;i<stores;i++)db.update("insert into store values(?,1,1,?)",10+i,"Store"+i);
  request();scope(1L,10L);counter.count=0;
  assertEquals(stores,rbac.accessibleStores(1L,1L).size());assertEquals(4,counter.count);
  rbac.require(PermissionAction.EMPLOYEE_MANAGE);rbac.context();assertEquals(4,counter.count);
  org.springframework.mock.web.MockHttpServletRequest next=request();counter.count=0;
  com.junzhou.service.impl.TenantScopeServiceImpl scopes=new com.junzhou.service.impl.TenantScopeServiceImpl(tenantMapper);
  ReflectionTestUtils.setField(scopes,"rbac",rbac);RequestScope.set(scopes.forEmployee(1L,10L));
  rbac.require(PermissionAction.EMPLOYEE_MANAGE);rbac.context();assertEquals(5,counter.count);
  RbacRequestContext.clear(next);
 }
 @Test void requestRevocationAndIdentityIsolation(){
  org.springframework.mock.web.MockHttpServletRequest r=request();scope(2L,10L);
  assertTrue(rbac.permits(1L,2L,10L,PermissionAction.ORDER_ACCEPT));
  assertFalse(rbac.permits(1L,1L,10L,PermissionAction.ORDER_ACCEPT));
  db.update("delete from role_permission where role_code='CLERK'");
  assertTrue(rbac.permits(1L,2L,10L,PermissionAction.ORDER_ACCEPT));
  RbacRequestContext.clear(r);RbacRequestContext.begin(r);
  assertFalse(rbac.permits(1L,2L,10L,PermissionAction.ORDER_ACCEPT));
 }
 @Test void lockedMutationRejectsRevokedEntrySnapshot(){
  request();scope(1L,10L);rbac.require(PermissionAction.EMPLOYEE_MANAGE);
  db.update("delete from role_permission where role_code='MERCHANT_ADMIN'");
  assertThrows(SecurityException.class,()->rbac.replacePermissions("CLERK",Collections.emptyList()));
  assertEquals(2,db.queryForObject("select count(*) from role_permission where role_code='CLERK'",Integer.class));
  assertNull(RbacRequestContext.current());
 }
 @Test void sharedActionsAndRoleBoundariesRemainDistinct(){
  db.update("insert into role_permission values(1,'CLERK','dish:update')");
  db.update("insert into employee_role values(1,2,'OTHER','CURRENT_STORE')");
  db.update("insert into employee_role_store values(1,2,'OTHER',11)");
  request();scope(2L,10L);RequestScope before=RequestScope.current();
  assertTrue(rbac.permits(1L,2L,10L,PermissionAction.DISH_UPDATE));
  assertFalse(rbac.permits(1L,2L,11L,PermissionAction.ORDER_ACCEPT));
  assertThrows(SecurityException.class,()->rbac.require(PermissionAction.DISH_UPDATE));
  assertFalse(rbac.context().toString().contains("dish:update"));assertSame(before,RequestScope.current());
 }
 @Test void disabledAndUnknownGrantsDenyAccess(){
  db.update("update employee_role set data_scope='UNKNOWN' where employee_id=2");
  assertTrue(rbac.accessibleStores(1L,2L).isEmpty());
  db.update("update employee set status=0 where id=1");
  assertTrue(rbac.accessibleStores(1L,1L).isEmpty());
 }
 @Test void requestScopeFailureAndAsyncExitClearSnapshot()throws Exception{
  org.springframework.mock.web.MockHttpServletRequest r=request();scope(1L,10L);rbac.context();
  com.junzhou.service.TenantScopeService scopes=mock(com.junzhou.service.TenantScopeService.class);
  com.junzhou.interceptor.RequestScopeInterceptor interceptor=new com.junzhou.interceptor.RequestScopeInterceptor(scopes);
  interceptor.afterConcurrentHandlingStarted(r,new org.springframework.mock.web.MockHttpServletResponse(),this);
  assertNull(RbacRequestContext.current());
  RbacRequestContext.begin(r);scope(1L,10L);rbac.context();
  r.setRequestURI("/admin/rbac/context");
  com.junzhou.context.BaseContext.removeCurrentId();
  assertFalse(interceptor.preHandle(r,new org.springframework.mock.web.MockHttpServletResponse(),this));
  assertNull(RbacRequestContext.current());
 }

 @Test void outerTransactionRollbackClearsAnyReloadedSnapshot(){
  request();scope(1L,10L);
  org.springframework.transaction.support.TransactionTemplate template=new org.springframework.transaction.support.TransactionTemplate(tx);
  template.execute(status->{
   rbac.replacePermissions("CLERK",Collections.singletonList("order:accept"));
   rbac.context();assertNotNull(RbacRequestContext.current());status.setRollbackOnly();return null;
  });
  assertNull(RbacRequestContext.current());
  assertEquals(2,db.queryForObject("select count(*) from role_permission where role_code='CLERK'",Integer.class));
 }
 @Test void snapshotCollectionsCannotBeChangedByCallers(){
  request();scope(1L,10L);rbac.context();AuthorizationSnapshot snapshot=RbacRequestContext.current();
  assertThrows(UnsupportedOperationException.class,()->snapshot.accessibleStores().clear());
  assertTrue(snapshot.permits(10L,PermissionAction.EMPLOYEE_MANAGE));
 }
}
