package com.junzhou.test;
import com.junzhou.context.RequestScope;

import com.junzhou.controller.admin.EmployeeController;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.entity.Employee;
import com.junzhou.exception.AccountLockedException;
import com.junzhou.mapper.EmployeeMapper;
import com.junzhou.mapper.AdminSessionMapper;
import com.junzhou.service.impl.EmployeeServiceImpl;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.DigestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmployeeStatusTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private EmployeeMapper mapper;
    private EmployeeServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AdminSessionMapper.class);
        try (InputStream xml = new ClassPathResource("mapper/EmployeeMapper.xml").getInputStream()) {
            new XMLMapperBuilder(xml, configuration, "mapper/EmployeeMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        // Keep the session connection open so the in-memory database lives for the test.
        session.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table employee (id bigint primary key, tenant_id bigint not null, username varchar(32), "
                + "name varchar(32), password varchar(64), phone varchar(32), sex varchar(2), "
                + "id_number varchar(32), status int, create_time timestamp, update_time timestamp, "
                + "create_user bigint, update_user bigint)");
        jdbc.execute("create table admin_session (session_id varchar(36) primary key, employee_id bigint, expires_at bigint)");
        String password = DigestUtils.md5DigestAsHex("123456".getBytes(StandardCharsets.UTF_8));
        for (long id = 1; id <= 2; id++) {
            jdbc.update("insert into employee values (?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, "employee" + id, "Employee " + id, password, "13800000000", "1",
                    "123456789012345678", 1, LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 1, 1, 0, 0), 99L, 99L);
        }
        RequestScope.set(new RequestScope(1L,null,1L,1L,java.util.Collections.singleton(1L)));
        mapper = session.getMapper(EmployeeMapper.class);
        service = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(service,"rbac",org.mockito.Mockito.mock(com.junzhou.service.RbacService.class));
        ReflectionTestUtils.setField(service, "employeeMapper", mapper);
        ReflectionTestUtils.setField(service, "adminSessionMapper", session.getMapper(AdminSessionMapper.class));
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        RequestScope.clear();
        if (session != null) {
            session.close();
        }
    }

    @Test
    void employeeAndSessionOperationsCannotCrossTenantAndInsertUsesTrustedTenant() {
        jdbc.update("update employee set tenant_id=2 where id=2");
        AdminSessionMapper sessions = session.getMapper(AdminSessionMapper.class);
        jdbc.update("insert into admin_session values ('a',1,9999999999999),('b',2,9999999999999)");
        assertNull(mapper.getById(2L));
        mapper.update(Employee.builder().id(2L).name("forged").status(0).tenantId(1L).build());
        assertEquals(0, mapper.updatePassword(2L, row(2).get("PASSWORD").toString(), "forged"));
        assertEquals(0, sessions.deleteCurrent("b",2L));
        assertEquals(0, sessions.deleteByEmployeeId(2L));
        assertEquals("Employee 2",row(2).get("NAME"));
        assertEquals(1,row(2).get("STATUS"));
        jdbc.execute("alter table employee alter column id bigint generated by default as identity (start with 10)");
        mapper.insert(Employee.builder().tenantId(2L).name("New").username("new").password("password").status(1).build());
        assertEquals(1L,jdbc.queryForObject("select tenant_id from employee where username='new'",Long.class));
        RequestScope.set(new RequestScope(2L,null,2L,2L,java.util.Collections.singleton(2L)));
        assertNull(mapper.getById(1L));
        assertEquals(1,sessions.deleteCurrent("b",2L));
        RequestScope.clear();
        assertThrows(RuntimeException.class, () -> mapper.getById(1L));
        assertThrows(RuntimeException.class, () -> sessions.deleteByEmployeeId(1L));
        assertNotNull(mapper.getForSessionIssue(1L,row(1).get("PASSWORD").toString()));
        assertNull(mapper.getForSessionIssue(1L,"wrong"));
        assertEquals(0,sessions.insert("forged",1L,9999999999999L,2L));
        assertEquals(1,sessions.insert("valid",1L,9999999999999L,1L));
    }

    @Test
    void disablingBlocksLoginAndEnablingRestoresItWithoutChangingOtherData() throws Exception {
        Map<String, Object> targetBefore = row(1);
        Map<String, Object> otherBefore = row(2);
        EmployeeLoginDTO login = new EmployeeLoginDTO();
        login.setUsername("employee1");
        login.setPassword("123456");
        assertEquals(1L, service.login(login).getId());

        mvc.perform(post("/admin/employee/status/0").param("id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(0, row(1).get("STATUS"));
        assertThrows(AccountLockedException.class, () -> service.login(login));
        targetBefore.put("STATUS", 0);
        assertEquals(targetBefore, row(1));
        assertEquals(otherBefore, row(2));

        mvc.perform(post("/admin/employee/status/1").param("id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(1L, service.login(login).getId());
        targetBefore.put("STATUS", 1);
        assertEquals(targetBefore, row(1));
        assertEquals(otherBefore, row(2));
    }

    @Test
    void unknownEmployeeDoesNotChangeAnyAccount() {
        Map<String, Object> first = row(1);
        Map<String, Object> second = row(2);
        assertThrows(com.junzhou.exception.AccountNotFoundException.class, () -> service.startOrStop(0, 999L));
        assertEquals(first, row(1));
        assertEquals(second, row(2));
    }

    @Test
    void dynamicUpdatePersistsProvidedFieldsAndPreservesCreationFields() {
        Map<String, Object> before = row(1);
        Map<String, Object> otherBefore = row(2);
        LocalDateTime updated = LocalDateTime.of(2026, 9, 21, 12, 0);
        mapper.update(Employee.builder().id(1L).name("New name").username("newuser")
                .password("new-password").phone("13900000000").sex("0")
                .idNumber("987654321098765432").status(0).updateTime(updated).updateUser(88L).build());
        Employee employee = mapper.getByUsername("newuser");
        assertEquals("New name", employee.getName());
        assertEquals("new-password", employee.getPassword());
        assertEquals("13900000000", employee.getPhone());
        assertEquals("0", employee.getSex());
        assertEquals("987654321098765432", employee.getIdNumber());
        assertEquals(0, employee.getStatus());
        assertEquals(updated, employee.getUpdateTime());
        assertEquals(88L, employee.getUpdateUser());
        assertEquals(before.get("CREATE_TIME"), row(1).get("CREATE_TIME"));
        assertEquals(before.get("CREATE_USER"), row(1).get("CREATE_USER"));
        assertEquals(otherBefore, row(2));
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select * from employee where id = ?", id);
    }
}
