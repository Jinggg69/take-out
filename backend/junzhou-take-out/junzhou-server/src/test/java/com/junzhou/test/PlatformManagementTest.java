package com.junzhou.test;

import com.junzhou.service.PasswordHashService;
import com.junzhou.service.impl.PasswordHashServiceImpl;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformManagementTest {
    JdbcTemplate db; Object service; TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        JdbcDataSource ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("create table tenant(id bigint auto_increment primary key,code varchar(64) unique,name varchar(100),status int default 1,deleted_at timestamp)");
        db.execute("create table store(id bigint auto_increment primary key,tenant_id bigint,code varchar(64),name varchar(100),status int default 1,deleted_at timestamp,unique(tenant_id,code))");
        db.execute("create table store_settings(tenant_id bigint,store_id bigint,version bigint default 0,address varchar(255),latitude decimal(9,6),longitude decimal(9,6),primary key(tenant_id,store_id))");
        db.execute("create table employee(id bigint auto_increment primary key,tenant_id bigint,username varchar(32) unique,password varchar(255),name varchar(32),phone varchar(11),sex varchar(2),id_number varchar(18),status int,create_time timestamp,update_time timestamp)");
        db.execute("create table rbac_role(tenant_id bigint,code varchar(64),name varchar(100),primary key(tenant_id,code))");
        db.execute("create table rbac_permission(tenant_id bigint,code varchar(64),name varchar(100),primary key(tenant_id,code))");
        db.execute("create table role_permission(tenant_id bigint,role_code varchar(64),permission_code varchar(64))");
        db.execute("create table employee_role(tenant_id bigint,employee_id bigint,role_code varchar(64),data_scope varchar(32))");
        db.execute("create table employee_store(tenant_id bigint,employee_id bigint,store_id bigint)");
        db.execute("create table payment_account(id varchar(64) primary key,tenant_id bigint,store_id bigint,mchid varchar(64),appid varchar(64))");
        db.execute("create table store_payment_account(tenant_id bigint,store_id bigint,payment_account_id varchar(64))");
        db.execute("create table admin_session(employee_id bigint)");
        db.execute("create table orders(id bigint primary key,tenant_id bigint,store_id bigint,status int,pay_status int,refund_status varchar(16),wechat_trade_state varchar(32))");
        db.execute("create table refund_request(order_id bigint,status varchar(16))");
        db.execute("create table platform_audit(id bigint auto_increment primary key,admin_id bigint,action varchar(64),target_type varchar(16),target_id bigint,created_at timestamp)");
        Class<?> cls=Class.forName("com.junzhou.platform.PlatformManagementService");
        service=cls.getConstructor(JdbcTemplate.class,PasswordHashService.class,Environment.class).newInstance(db,new PasswordHashServiceImpl(),new MockEnvironment().withProperty("sky.payment.channel","mock"));
        Class.forName("com.junzhou.platform.PlatformContext").getMethod("setAdminId",Long.class).invoke(null,99L);
    }
    @AfterEach void clear() throws Exception { try { Class.forName("com.junzhou.platform.PlatformContext").getMethod("clear").invoke(null); } catch(ClassNotFoundException ignored) {} }
    Object call(String name,Class<?>[] types,Object... args) { return tx.execute(s->{try{return service.getClass().getMethod(name,types).invoke(service,args);}catch(InvocationTargetException e){throw (RuntimeException)e.getCause();}catch(Exception e){throw new RuntimeException(e);}}); }
    Map<String,Object> store(String code) { Map<String,Object> m=new HashMap<>();m.put("code",code);m.put("name","门店");m.put("address","街道1号");return m; }
    Map<String,Object> tenant(String code) { Map<String,Object> m=new HashMap<>();m.put("code",code);m.put("name","商户");m.put("adminUsername",code+"_admin");m.put("adminName","管理员");m.put("adminPassword","Safe-password-123!");m.put("store",store("first"));return m; }
    Long create(String code) { return ((Number)call("createTenant",new Class[]{Map.class},tenant(code))).longValue(); }
    @Test void onboardingCreatesCompleteIndependentOwnershipAndHashedAdmin() {
        Long first=create("first"),second=create("second");
        assertNotEquals(first,second);
        assertEquals(2,db.queryForObject("select count(*) from payment_account",Integer.class));
        assertEquals(com.junzhou.security.PermissionAction.values().length,db.queryForObject("select count(*) from role_permission where tenant_id=? and role_code='MERCHANT_ADMIN'",Integer.class,first));
        assertEquals("TENANT_STORES",db.queryForObject("select data_scope from employee_role where tenant_id=?",String.class,first));
        assertTrue(new PasswordHashServiceImpl().matches("Safe-password-123!",db.queryForObject("select password from employee where tenant_id=?",String.class,first)));
        assertEquals(2,db.queryForObject("select count(*) from platform_audit where admin_id=99",Integer.class));
    }
    @Test void duplicateUsernameRollsBackEntireOnboardingWithoutForeignKeyHelp() {
        create("first"); Map<String,Object> other=tenant("second");other.put("adminUsername","first_admin");
        assertThrows(RuntimeException.class,()->call("createTenant",new Class[]{Map.class},other));
        assertEquals(1,db.queryForObject("select count(*) from tenant",Integer.class));
        assertEquals(1,db.queryForObject("select count(*) from store",Integer.class));
        assertEquals(1,db.queryForObject("select count(*) from platform_audit",Integer.class));
    }
    @Test void duplicateTenantCodeDoesNotCreateExtraRows() {
        create("first"); assertThrows(RuntimeException.class,()->create("first"));
        assertEquals(1,db.queryForObject("select count(*) from tenant",Integer.class));
    }
    @Test void pendingOrdersAndFailedRefundsBlockDeletionButHistorySurvivesSuccessfulDeletion() {
        Long id=create("first"); Long store=db.queryForObject("select id from store where tenant_id=?",Long.class,id);
        db.update("insert into orders values(1,?,?,1,0,null,null)",id,store);
        assertThrows(RuntimeException.class,()->call("deleteTenant",new Class[]{Long.class},id));
        db.update("update orders set status=6,pay_status=4,refund_status='FAILED'");
        assertThrows(RuntimeException.class,()->call("deleteTenant",new Class[]{Long.class},id));
        db.update("update orders set status=5,pay_status=1,refund_status=null");
        call("deleteTenant",new Class[]{Long.class},id);
        assertEquals(1,db.queryForObject("select count(*) from orders",Integer.class));
        assertEquals(0,db.queryForObject("select status from store where id=?",Integer.class,store));
        assertThrows(RuntimeException.class,()->call("tenantStatus",new Class[]{Long.class,Integer.class},id,1));
    }
    @Test void disablingTenantRevokesSessionsAndRequiresExplicitStoreReopening() {
        Long id=create("first");Long employee=db.queryForObject("select id from employee where tenant_id=?",Long.class,id);
        db.update("insert into admin_session values(?)",employee);
        call("tenantStatus",new Class[]{Long.class,Integer.class},id,0);
        assertEquals(0,db.queryForObject("select status from store where tenant_id=?",Integer.class,id));
        assertEquals(0,db.queryForObject("select count(*) from admin_session",Integer.class));
        Map<String,Object> additional=store("blocked");additional.put("tenantId",id);assertThrows(RuntimeException.class,()->call("createStore",new Class[]{Map.class},additional));
        call("tenantStatus",new Class[]{Long.class,Integer.class},id,1);
        assertEquals(0,db.queryForObject("select status from store where tenant_id=?",Integer.class,id));
    }
    @Test void storeCrudStaysUnderItsOriginalTenantAndPagesFilterOwnership() {
        Long first=create("first"),second=create("second");Map<String,Object> form=store("second_store");form.put("tenantId",first);
        Long id=((Number)call("createStore",new Class[]{Map.class},form)).longValue();
        com.junzhou.result.PageResult page=(com.junzhou.result.PageResult)call("stores",new Class[]{int.class,int.class,String.class,Long.class},1,10,"second_store",first);
        assertEquals(1,page.getTotal());
        form.put("tenantId",second);
        assertThrows(RuntimeException.class,()->call("updateStore",new Class[]{Long.class,Map.class},id,form));
        assertEquals(first,db.queryForObject("select tenant_id from store where id=?",Long.class,id));
        form.put("tenantId",first);form.put("name","修改后的门店");
        call("updateStore",new Class[]{Long.class,Map.class},id,form);
        assertEquals("修改后的门店",db.queryForObject("select name from store where id=?",String.class,id));
        call("deleteStore",new Class[]{Long.class},id);
        assertThrows(RuntimeException.class,()->call("storeStatus",new Class[]{Long.class,Integer.class},id,1));
        page=(com.junzhou.result.PageResult)call("stores",new Class[]{int.class,int.class,String.class,Long.class},1,10,"second_store",first);
        assertEquals(0,page.getTotal());
    }
    @Test void reservedUsernamesAndInvalidLocationsLeaveNoPartialTenant() {
        Map<String,Object> form=tenant("first");form.put("adminUsername","PLATFORM_reserved");
        assertThrows(RuntimeException.class,()->call("createTenant",new Class[]{Map.class},form));
        form.put("adminUsername","valid_admin");((Map<String,Object>)form.get("store")).put("latitude",20);
        assertThrows(RuntimeException.class,()->call("createTenant",new Class[]{Map.class},form));
        assertEquals(0,db.queryForObject("select count(*) from tenant",Integer.class));
    }
    @Test void realPaymentModeNeverFabricatesMerchantCredentials() throws Exception {
        service=service.getClass().getConstructor(JdbcTemplate.class,PasswordHashService.class,Environment.class).newInstance(db,new PasswordHashServiceImpl(),new MockEnvironment().withProperty("sky.payment.channel","wechat"));
        create("first");assertEquals(0,db.queryForObject("select count(*) from payment_account",Integer.class));
    }
    @Test void unfinishedRefundRecordBlocksStoreDeletionEvenForCompletedOrder() {
        Long tenant=create("first");Long id=db.queryForObject("select id from store where tenant_id=?",Long.class,tenant);
        db.update("insert into orders values(1,?,?,5,1,null,null)",tenant,id);db.update("insert into refund_request values(1,'FAILED')");
        assertThrows(RuntimeException.class,()->call("deleteStore",new Class[]{Long.class},id));
        assertNull(db.queryForObject("select deleted_at from store where id=?",java.sql.Timestamp.class,id));
    }
    @Test void cancelledPaymentWithUnknownProviderOutcomeCannotBeDeleted() {
        Long tenant=create("first");Long id=db.queryForObject("select id from store where tenant_id=?",Long.class,tenant);
        db.update("insert into orders values(1,?,?,6,0,null,null)",tenant,id);
        assertThrows(RuntimeException.class,()->call("deleteStore",new Class[]{Long.class},id));
        db.update("update orders set wechat_trade_state='CLOSED'");
        call("deleteStore",new Class[]{Long.class},id);
        assertEquals(1,db.queryForObject("select count(*) from orders",Integer.class));
    }
    @Test void deleteWaitsForSubmissionStoreBarrierThenSeesCommittedOrder() throws Exception {
        Long tenant=create("first");Long id=db.queryForObject("select id from store where tenant_id=?",Long.class,tenant);
        java.util.concurrent.ExecutorService workers=java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>> attempt=new java.util.concurrent.atomic.AtomicReference<>();
        try {
            tx.execute(state->{
                db.queryForObject("select id from store where id=? for update",Long.class,id);
                db.update("insert into orders values(1,?,?,1,0,null,null)",tenant,id);
                attempt.set(workers.submit(()->{
                    try {
                        Class.forName("com.junzhou.platform.PlatformContext").getMethod("setAdminId",Long.class).invoke(null,99L);
                        started.countDown();
                        assertThrows(RuntimeException.class,()->call("deleteStore",new Class[]{Long.class},id));
                    } catch(Exception e) {throw new RuntimeException(e);} finally {try {Class.forName("com.junzhou.platform.PlatformContext").getMethod("clear").invoke(null);}catch(Exception ignored){}}
                }));
                try {assertTrue(started.await(2,java.util.concurrent.TimeUnit.SECONDS));assertThrows(java.util.concurrent.TimeoutException.class,()->attempt.get().get(150,java.util.concurrent.TimeUnit.MILLISECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
                return null;
            });
            attempt.get().get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertNull(db.queryForObject("select deleted_at from store where id=?",java.sql.Timestamp.class,id));
        } finally {workers.shutdownNow();}
    }
    @Test void merchantContextAloneCannotManagePlatform() throws Exception {
        Class.forName("com.junzhou.platform.PlatformContext").getMethod("clear").invoke(null);
        assertThrows(RuntimeException.class,()->create("forbidden"));
        assertEquals(0,db.queryForObject("select count(*) from tenant",Integer.class));
    }
}




