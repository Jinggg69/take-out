package com.sky.test;

import com.sky.config.WebMvcConfiguration;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.controller.admin.EmployeeController;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.LoginFailedException;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.properties.JwtProperties;
import com.sky.service.EmployeeService;
import com.sky.service.AdminSessionService;
import com.sky.utils.JwtUtil;
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
        context.register(DatabaseConfiguration.class, WebMvcConfiguration.class, EmployeeController.class,
                JwtTokenAdminInterceptor.class, JwtTokenUserInterceptor.class, GlobalExceptionHandler.class, Probe.class);
        context.refresh();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("create table employee (id bigint primary key, username varchar(32), name varchar(32), "
                + "password varchar(64), phone varchar(32), sex varchar(2), id_number varchar(32), status int, "
                + "create_time timestamp, update_time timestamp, create_user bigint, update_user bigint)");
        jdbc.execute("create table admin_session (session_id varchar(36) primary key, employee_id bigint not null, "
                + "expires_at bigint not null)");
        for (long id : new long[]{42,84}) {
            jdbc.update("insert into employee (id,username,name,password,status) values (?,?,?,?,1)",
                    id, "employee" + id, "Employee " + id, hash("Old123"));
        }
        employees = context.getBean(EmployeeService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        context.close();
    }

    @Test
    void loginCreatesIndependentPersistentSessionIdentifiers() throws Exception {
        String first = login(42, "Old123");
        String second = login(42, "Old123");
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
        employees.startOrStop(0, 42L);
        rejected(first);
        rejected(second);
        accepted(other, 84);
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
        rejectSessionDeletes();
        BaseContext.setCurrentId(42L);
        PasswordEditDTO input = new PasswordEditDTO();
        input.setOldPassword("Old123");
        input.setNewPassword("New456");
        assertThrows(RuntimeException.class, () -> employees.editPassword(input));
        assertEquals(hash("Old123"), jdbc.queryForObject("select password from employee where id=42", String.class));
        accepted(token, 42);
    }

    @Test
    void failedSessionRevocationRollsBackAccountDisable() throws Exception {
        String token = login(42, "Old123");
        rejectSessionDeletes();
        assertThrows(RuntimeException.class, () -> employees.startOrStop(0, 42L));
        assertEquals(1, jdbc.queryForObject("select status from employee where id=42", Integer.class));
        accepted(token, 42);
    }

    @Test
    void aPasswordChangeBetweenCredentialCheckAndIssuanceRejectsTheOldSnapshot() throws Exception {
        Employee snapshot = authenticatedEmployee();
        String token = login(42, "Old123");
        mvc.perform(put("/admin/employee/editPassword").header("token", token)
                .contentType("application/json").content("{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertThrows(LoginFailedException.class, () -> context.getBean(AdminSessionService.class).issue(snapshot));
        assertEquals(0, jdbc.queryForObject("select count(*) from admin_session", Integer.class));
    }

    @Test
    void concurrentIssuanceWaitsForEmployeeLockAndCannotEscapeDisable() throws Exception {
        Employee snapshot = authenticatedEmployee();
        login(42, "Old123");
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
                // Observe an actual database lock wait rather than infer it from a slow thread.
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean blocked = false;
                while (System.nanoTime() < deadline && !issued.isDone()) {
                    if (jdbc.queryForObject("select count(*) from information_schema.sessions "
                            + "where blocker_id is not null", Integer.class) > 0) {
                        blocked = true;
                        break;
                    }
                    Thread.yield();
                }
                assertTrue(blocked, "Session issuance must wait for the employee row lock");
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
    @MapperScan("com.sky.mapper")
    @ComponentScan(basePackages="com.sky.service", useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,
                    pattern={"com\\.sky\\.service\\.impl\\.EmployeeServiceImpl", "com\\.sky\\.service\\.AdminSessionService"}))
    static class DatabaseConfiguration {
        @Bean DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return source;
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setTypeAliasesPackage("com.sky.entity");
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
