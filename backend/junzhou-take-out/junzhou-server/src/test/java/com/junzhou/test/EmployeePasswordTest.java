package com.junzhou.test;

import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.controller.admin.EmployeeController;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.exception.PasswordErrorException;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.interceptor.JwtTokenAdminInterceptor;
import com.junzhou.mapper.EmployeeMapper;
import com.junzhou.mapper.AdminSessionMapper;
import com.junzhou.service.AdminSessionService;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.impl.EmployeeServiceImpl;
import com.junzhou.utils.JwtUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, authentication, service and mapper against an isolated database. */
class EmployeePasswordTest {
    private static final String PATH = "/admin/employee/editPassword";
    private static final String SECRET = "employee-password-test-secret";
    private SqlSession session;
    private JdbcTemplate jdbc;
    private EmployeeServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        BaseContext.removeCurrentId();
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        Configuration config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(AdminSessionMapper.class);
        try (InputStream xml = new ClassPathResource("mapper/EmployeeMapper.xml").getInputStream()) {
            new XMLMapperBuilder(xml, config, "mapper/EmployeeMapper.xml", config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession(true);
        session.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table employee (id bigint primary key, username varchar(32), name varchar(32), "
                + "password varchar(64), phone varchar(32), sex varchar(2), id_number varchar(32), status int, "
                + "create_time timestamp, update_time timestamp, create_user bigint, update_user bigint)");
        jdbc.execute("create table admin_session (session_id varchar(36) primary key, employee_id bigint, expires_at bigint)");
        for (long id : new long[]{42, 84}) {
            jdbc.update("insert into employee (id, username, name, password, status, create_user, update_user) "
                    + "values (?, ?, ?, ?, 1, 99, 99)", id, "employee" + id, "Employee " + id, hash("Old123"));
        }
        service = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(service, "employeeMapper", session.getMapper(EmployeeMapper.class));
        ReflectionTestUtils.setField(service, "adminSessionMapper", session.getMapper(AdminSessionMapper.class));
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", service);
        JwtProperties properties = new JwtProperties();
        properties.setAdminTokenName("token");
        properties.setAdminSecretKey(SECRET);
        JwtTokenAdminInterceptor interceptor = new JwtTokenAdminInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtProperties", properties);
        AdminSessionService sessions = new AdminSessionService();
        ReflectionTestUtils.setField(sessions, "adminSessionMapper", session.getMapper(AdminSessionMapper.class));
        ReflectionTestUtils.setField(interceptor, "adminSessionService", sessions);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
                .addInterceptors(interceptor).build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        if (session != null) session.close();
    }

    @Test
    void signedEmployeeCanChangeOwnPasswordWithoutSendingAnEmployeeId() throws Exception {
        Map<String, Object> other = jdbc.queryForMap("select * from employee where id = 84");
        change(42, "{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}", 1);
        assertEquals(hash("New456"), password(42));
        assertEquals(other, jdbc.queryForMap("select * from employee where id = 84"));
        assertEquals("Employee 42", jdbc.queryForObject("select name from employee where id=42", String.class));
        assertEquals(99L, jdbc.queryForObject("select create_user from employee where id=42", Long.class));
        assertEquals(42L, jdbc.queryForObject("select update_user from employee where id=42", Long.class));
        assertNotNull(jdbc.queryForObject("select update_time from employee where id=42", java.sql.Timestamp.class));
        EmployeeLoginDTO login = new EmployeeLoginDTO();
        login.setUsername("employee42");
        login.setPassword("Old123");
        assertThrows(PasswordErrorException.class, () -> service.login(login));
        login.setPassword("New456");
        assertEquals(42L, service.login(login).getId());
    }

    @Test
    void wrongOldPasswordDoesNotMutateAnyAccount() throws Exception {
        change(42, "{\"oldPassword\":\"Wrong1\",\"newPassword\":\"New456\"}", 0);
        assertEquals(hash("Old123"), password(42));
        assertEquals(hash("Old123"), password(84));
    }

    @Test
    void clientCannotSelectAnotherEmployeeEvenWithTheCorrectOldPassword() throws Exception {
        change(42, "{\"empId\":84,\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}", 0);
        assertEquals(hash("Old123"), password(42));
        assertEquals(hash("Old123"), password(84));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "12345", "123456789012345678901", "abc 123", "abc-123"})
    void invalidNewPasswordIsRejectedWithoutMutation(String newPassword) throws Exception {
        change(42, "{\"oldPassword\":\"Old123\",\"newPassword\":\"" + newPassword + "\"}", 0);
        assertEquals(hash("Old123"), password(42));
    }

    @Test
    void missingOldPasswordIsRejected() throws Exception {
        change(42, "{\"newPassword\":\"New456\"}", 0);
        assertEquals(hash("Old123"), password(42));
    }

    @Test
    void disabledOrMissingEmployeeCannotChangePassword() throws Exception {
        jdbc.update("update employee set status=0 where id=42");
        request(42, "{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}").andExpect(status().isUnauthorized());
        request(999, "{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}").andExpect(status().isUnauthorized());
        assertEquals(hash("Old123"), password(42));
    }

    @Test
    void aConsumedOldPasswordCannotOverwriteTheNewPassword() throws Exception {
        change(42, "{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}", 1);
        change(42, "{\"oldPassword\":\"Old123\",\"newPassword\":\"Other789\"}", 0);
        assertEquals(hash("New456"), password(42));
    }

    @Test
    void anonymousRequestIsRejectedBeforeAnyDatabaseMutation() throws Exception {
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                .content("{\"oldPassword\":\"Old123\",\"newPassword\":\"New456\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(hash("Old123"), password(42));
    }

    private void change(long id, String body, int code) throws Exception {
        request(id, body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }

    private ResultActions request(long id, String body) throws Exception {
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, id);
        String sessionId = UUID.randomUUID().toString();
        claims.put("jti", sessionId);
        String token = JwtUtil.createJWT(SECRET, 60000L, claims);
        jdbc.update("insert into admin_session values (?,?,?)", sessionId, id, System.currentTimeMillis()+60000);
        return mvc.perform(put(PATH).header("token", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String password(long id) {
        return jdbc.queryForObject("select password from employee where id=?", String.class, id);
    }

    private String hash(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }
}
