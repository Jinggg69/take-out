package com.junzhou.test;

import com.junzhou.config.WebMvcConfiguration;
import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.controller.admin.EmployeeController;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.dto.PasswordEditDTO;
import com.junzhou.entity.Employee;
import com.junzhou.exception.LoginFailedException;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.interceptor.JwtTokenAdminInterceptor;
import com.junzhou.interceptor.JwtTokenUserInterceptor;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.EmployeeService;
import com.junzhou.service.AdminSessionService;
import com.junzhou.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.h2.api.Trigger;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.apache.ibatis.session.SqlSessionFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Authentication boundaries exercised through real MVC, SQL and Spring transactions. */
class AdminAuthenticationTest {
    private static final String SECRET = "admin-auth-regression-secret";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JdbcTemplate jdbc;
    private EmployeeService employees;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        BaseContext.removeCurrentId();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(com.junzhou.test.support.LegacyMvcScopeConfiguration.class);
        context.register(DatabaseConfiguration.class, WebMvcConfiguration.class, EmployeeController.class,
                JwtTokenAdminInterceptor.class, JwtTokenUserInterceptor.class, GlobalExceptionHandler.class, Probe.class);
        context.refresh();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("create table tenant(id bigint primary key,status int); insert into tenant values(1,1)");
        jdbc.execute("create table employee (id bigint primary key, tenant_id bigint not null, username varchar(32), name varchar(32), "
                + "password varchar(255), phone varchar(32), sex varchar(2), id_number varchar(32), status int, "
                + "create_time timestamp, update_time timestamp, create_user bigint, update_user bigint)");
        jdbc.execute("create table admin_session (session_id varchar(36) primary key, employee_id bigint not null, "
                + "expires_at bigint not null)");
        for (long id : new long[]{42,84}) {
            jdbc.update("insert into employee (tenant_id,id,username,name,password,status) values (1,?,?,?,?,1)",
                    id, "employee" + id, "Employee " + id, hash("Old123"));
        }
        employees = context.getBean(EmployeeService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        RequestScope.clear();
        BaseContext.removeCurrentId();
        context.close();
    }

    @Test
    void failedLoginAndFailedUpgradeKeepLegacyCredentialAndIssueNoSession() {
        EmployeeLoginDTO input = new EmployeeLoginDTO();
        input.setUsername("employee42");
        input.setPassword("incorrect");
        assertThrows(com.junzhou.exception.PasswordErrorException.class, () -> employees.loginWithSession(input));
        input.setPassword("Old123");
        jdbc.update("update employee set status=0 where id=42");
        assertThrows(com.junzhou.exception.AccountLockedException.class, () -> employees.loginWithSession(input));
        jdbc.update("update employee set status=1 where id=42");
        jdbc.execute("alter table employee add constraint legacy_only check(length(password)=32)");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> employees.loginWithSession(input));
        assertEquals(hash("Old123"), jdbc.queryForObject("select password from employee where id=42", String.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
    }

    @Test
    void loginCreatesIndependentPersistentSessionIdentifiers() throws Exception {
        String first = login(42, "Old123");
        String upgraded = jdbc.queryForObject("select password from employee where id=42", String.class);
        assertTrue(upgraded.startsWith("{bcrypt}"));
        String second = login(42, "Old123");
        assertEquals(upgraded, jdbc.queryForObject("select password from employee where id=42", String.class));
        Claims one = JwtUtil.parseJWT(SECRET, first);
        Claims two = JwtUtil.parseJWT(SECRET, second);
        assertNotNull(one.getId());
        assertNotEquals(one.getId(), two.getId());
        assertEquals(2, jdbc.queryForObject("select count(*) from admin_session where employee_id=42", Integer.class));
        assertEquals(one.getExpiration().getTime(), jdbc.queryForObject(
                "select expires_at from admin_session where session_id=?", Long.class, one.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from admin_session where session_id=?", Integer.class, first));
    }

    @Test
    void successfulAndExceptionalRequestsClearCurrentIdentity() throws Exception {
        String token = login(42, "Old123");
        BaseContext.setCurrentId(999L);
        mvc.perform(get("/admin/auth-probe").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value("42"));
        assertNull(BaseContext.getCurrentId());
        assertThrows(Exception.class, () -> mvc.perform(get("/admin/auth-probe/fail").header("token", token)));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void rejectedAndNonControllerRequestsCannotRetainAStaleIdentity() throws Exception {
        BaseContext.setCurrentId(999L);
        mvc.perform(get("/admin/auth-probe").header("token", "not-a-token"))
                .andExpect(status().isUnauthorized());
        assertNull(BaseContext.getCurrentId());
        BaseContext.setCurrentId(999L);
        assertTrue(context.getBean(JwtTokenAdminInterceptor.class).preHandle(
                new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void logoutRevokesOnlyTheCurrentSession() throws Exception {
        String first = login(42, "Old123");
        String second = login(42, "Old123");
        String other = login(84, "Old123");
        mvc.perform(post("/admin/employee/logout").header("token", first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        rejected(first);
        accepted(second, 42);
        accepted(other, 84);
        assertEquals(2, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
    }

    @Test
    void disableRevokesAllEmployeeSessionsAndReenableCannotRestoreThem() throws Exception {
        String first = login(42, "Old123");
        String second = login(42, "Old123");
        String other = login(84, "Old123");
        RequestScope.set(new RequestScope(42L,null,1L,1L,java.util.Collections.singleton(1L)));
        employees.startOrStop(0, 42L);
        rejected(first);
        rejected(second);
        accepted(other, 84);
        RequestScope.set(new RequestScope(42L,null,1L,1L,java.util.Collections.singleton(1L)));
        employees.startOrStop(1, 42L);
        rejected(first);
        rejected(second);
        accepted(login(42, "Old123"), 42);
    }

    @Test
    void passwordChangeRevokesEveryOldSessionButAllowsNewCredentials() throws Exception {
        String first = login(42, "Old123");
        String second = login(42, "Old123");
        mvc.perform(put("/admin/employee/editPassword").header("token", first)
                .contentType("application/json").content("{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        rejected(first);
        rejected(second);
        mvc.perform(post("/admin/employee/login").contentType("application/json")
                .content("{\"username\":\"employee42\",\"password\":\"Old123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        accepted(login(42, "New456"), 42);
    }

    @Test
    void incorrectOldPasswordDoesNotRevokeAnOtherwiseValidSession() throws Exception {
        String token = login(42, "Old123");
        mvc.perform(put("/admin/employee/editPassword").header("token", token)
                .contentType("application/json").content("{\"oldPassword\":\"Wrong1\",\"newPassword\":\"New456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        accepted(token, 42);
    }

    @Test
    void legacyUnregisteredMismatchedAndExpiredSessionsAreRejected() throws Exception {
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 42L);
        rejected(JwtUtil.createJWT(SECRET, 60000L, claims));
        claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 42L);
        claims.put("jti", UUID.randomUUID().toString());
        rejected(JwtUtil.createJWT(SECRET, 60000L, claims));
        String current = login(42, "Old123");
        claims = new HashMap<>(JwtUtil.parseJWT(SECRET, current));
        claims.put(JwtClaimsConstant.EMP_ID, 84L);
        rejected(JwtUtil.createJWT(SECRET, 60000L, claims));
        claims = new HashMap<>(JwtUtil.parseJWT(SECRET, current));
        rejected(JwtUtil.createJWT(SECRET, -60000L, claims));
        jdbc.update("update admin_session set expires_at=0 where employee_id=42");
        rejected(current);
    }

    @Test
    void databaseFailureFailsClosedWithNoCurrentIdentity() throws Exception {
        String token = login(42, "Old123");
        jdbc.execute("drop table admin_session");
        BaseContext.setCurrentId(999L);
        mvc.perform(get("/admin/auth-probe").header("token", token))
                .andExpect(status().isServiceUnavailable());
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void directlyDisabledOrDeletedEmployeeCannotUseAnExistingSession() throws Exception {
        String token = login(42, "Old123");
        jdbc.update("update employee set status=0 where id=42");
        rejected(token);
        jdbc.update("delete from employee where id=42");
        rejected(token);
    }

    @Test
    void failedSessionRevocationRollsBackPasswordChange() throws Exception {
        String token = login(42, "Old123");
        String storedBefore = jdbc.queryForObject("select password from employee where id=42", String.class);
        rejectSessionDeletes();
        BaseContext.setCurrentId(42L);
        RequestScope.set(new RequestScope(42L,null,1L,1L,java.util.Collections.singleton(1L)));
        PasswordEditDTO input = new PasswordEditDTO();
        input.setOldPassword("Old123");
        input.setNewPassword("New456");
        assertThrows(RuntimeException.class, () -> employees.editPassword(input));
        assertEquals(storedBefore, jdbc.queryForObject("select password from employee where id=42", String.class));
        accepted(token, 42);
    }

    @Test
    void failedSessionRevocationRollsBackAccountDisable() throws Exception {
        String token = login(42, "Old123");
        rejectSessionDeletes();
        RequestScope.set(new RequestScope(42L,null,1L,1L,java.util.Collections.singleton(1L)));
        assertThrows(RuntimeException.class, () -> employees.startOrStop(0, 42L));
        assertEquals(1, jdbc.queryForObject("select status from employee where id=42", Integer.class));
        accepted(token, 42);
    }

    @Test
    void aPasswordChangeBetweenCredentialCheckAndIssuanceRejectsTheOldSnapshot() throws Exception {
        String token = login(42, "Old123");
        Employee snapshot = authenticatedEmployee();
        mvc.perform(put("/admin/employee/editPassword").header("token", token)
                .contentType("application/json").content("{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThrows(LoginFailedException.class, () -> context.getBean(AdminSessionService.class).issue(snapshot));
        assertEquals(0, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
    }

    @Test
    void concurrentIssuanceWaitsForEmployeeLockAndCannotEscapeDisable() throws Exception {
        login(42, "Old123");
        Employee snapshot = authenticatedEmployee();
        AdminSessionService sessions = context.getBean(AdminSessionService.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<String> issued = new CompletableFuture<>();
        try {
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.execute(status -> {
                jdbc.queryForObject("select id from employee where id=42 for update", Long.class);
                executor.submit(() -> {
                    try { issued.complete(sessions.issue(snapshot)); }
                    catch (Throwable ex) { issued.completeExceptionally(ex); }
                });
                awaitLockWait(issued);
                RequestScope.set(new RequestScope(42L,null,1L,1L,java.util.Collections.singleton(1L)));
        employees.startOrStop(0, 42L);
                return null;
            });
            ExecutionException failure = assertThrows(ExecutionException.class, () -> issued.get(5, TimeUnit.SECONDS));
            assertInstanceOf(LoginFailedException.class, failure.getCause());
            assertEquals(0, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void firstLegacyLoginCannotOverwriteConcurrentPasswordChange() throws Exception {
        EmployeeLoginDTO login = new EmployeeLoginDTO();
        login.setUsername("employee42");
        login.setPassword("Old123");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> attempt = new CompletableFuture<>();
        try {
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.execute(status -> {
                jdbc.queryForObject("select id from tenant where id=1 for update", Long.class);
                executor.submit(() -> {
                    try {
                        employees.loginWithSession(login);
                        attempt.complete(null);
                    } catch (Throwable ex) {
                        attempt.completeExceptionally(ex);
                    }
                });
                // The old credential is verified; the login is now waiting to sign its session.
                awaitLockWait(attempt);
                BaseContext.setCurrentId(42L);
                RequestScope.set(new RequestScope(42L, null, 1L, null, java.util.Collections.emptySet()));
                PasswordEditDTO change = new PasswordEditDTO();
                change.setOldPassword("Old123");
                change.setNewPassword("New456");
                employees.editPassword(change);
                return null;
            });
            ExecutionException failure = assertThrows(ExecutionException.class, () -> attempt.get(5, TimeUnit.SECONDS));
            assertInstanceOf(LoginFailedException.class, failure.getCause());
            assertEquals(0, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
            BaseContext.removeCurrentId();
            RequestScope.clear();
            accepted(login(42, "New456"), 42);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private void awaitLockWait(CompletableFuture<?> attempt) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline && !attempt.isDone()) {
            if (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",
                    Integer.class) > 0) return;
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        fail("Expected the login transaction to wait for a database lock");
    }

    private Employee authenticatedEmployee() {
        EmployeeLoginDTO input = new EmployeeLoginDTO();
        input.setUsername("employee42");
        input.setPassword("Old123");
        return employees.login(input);
    }

    private void rejectSessionDeletes() {
        jdbc.execute("create trigger reject_revoke before delete on admin_session for each row call \""
                + RejectDelete.class.getName() + "\"");
    }

    private String login(long id, String password) throws Exception {
        MvcResult result = mvc.perform(post("/admin/employee/login").contentType("application/json")
                .content("{\"username\":\"employee" + id + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andReturn();
        assertNull(BaseContext.getCurrentId());
        return json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("token").asText();
    }

    private void accepted(String token, long id) throws Exception {
        mvc.perform(get("/admin/auth-probe").header("token", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(String.valueOf(id)));
        assertNull(BaseContext.getCurrentId());
    }

    private void rejected(String token) throws Exception {
        mvc.perform(get("/admin/auth-probe").header("token", token)).andExpect(status().isUnauthorized());
        assertNull(BaseContext.getCurrentId());
    }

    private static String hash(String password) {
        return DigestUtils.md5DigestAsHex(password.getBytes(StandardCharsets.UTF_8));
    }

    public static class RejectDelete implements Trigger {
        @Override public void init(Connection c,String schema,String name,String table,boolean before,int type) { }
        @Override public void fire(Connection c,Object[] before,Object[] after) throws SQLException { throw new SQLException("Simulated session deletion failure"); }
        @Override public void close() { }
        @Override public void remove() { }
    }

    @RestController
    static class Probe {
        @GetMapping("/admin/auth-probe") public String identity() { return String.valueOf(BaseContext.getCurrentId()); }
        @GetMapping("/admin/auth-probe/fail") public String fail() { throw new IllegalStateException("test failure"); }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.junzhou.mapper")
    @ComponentScan(basePackages="com.junzhou.service", useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,
                    pattern={"com\\.junzhou\\.service\\.impl\\.EmployeeServiceImpl", "com\\.junzhou\\.service\\.impl\\.AdminSessionServiceImpl"}))
    static class DatabaseConfiguration {
        @Bean com.junzhou.service.PasswordHashService passwordHashes() { return new com.junzhou.service.impl.PasswordHashServiceImpl(); }
        @Bean com.junzhou.service.RbacService rbacService(){return org.mockito.Mockito.mock(com.junzhou.service.RbacService.class);}

        @Bean DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return source;
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setTypeAliasesPackage("com.junzhou.entity");
            org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.setCacheEnabled(false);
            factory.setConfiguration(config);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
            return factory.getObject();
        }
        @Bean JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setAdminSecretKey(SECRET);
            properties.setAdminTokenName("token");
            properties.setAdminTtl(60000L);
            properties.setUserSecretKey("user-auth-test-secret");
            properties.setUserTokenName("authentication");
            return properties;
        }
    }
}
