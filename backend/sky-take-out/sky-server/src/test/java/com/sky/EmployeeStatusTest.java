package com.sky;

import com.sky.controller.admin.EmployeeController;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.mapper.EmployeeMapper;
import com.sky.service.impl.EmployeeServiceImpl;
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
        try (InputStream xml = new ClassPathResource("mapper/EmployeeMapper.xml").getInputStream()) {
            new XMLMapperBuilder(xml, configuration, "mapper/EmployeeMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        // Keep the session connection open so the in-memory database lives for the test.
        session.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table employee (id bigint primary key, username varchar(32), "
                + "name varchar(32), password varchar(64), phone varchar(32), sex varchar(2), "
                + "id_number varchar(32), status int, create_time timestamp, update_time timestamp, "
                + "create_user bigint, update_user bigint)");
        String password = DigestUtils.md5DigestAsHex("123456".getBytes(StandardCharsets.UTF_8));
        for (long id = 1; id <= 2; id++) {
            jdbc.update("insert into employee values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, "employee" + id, "Employee " + id, password, "13800000000", "1",
                    "123456789012345678", 1, LocalDateTime.of(2026, 1, 1, 0, 0),
                    LocalDateTime.of(2026, 1, 1, 0, 0), 99L, 99L);
        }
        mapper = session.getMapper(EmployeeMapper.class);
        service = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(service, "employeeMapper", mapper);
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
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
        service.startOrStop(0, 999L);
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
