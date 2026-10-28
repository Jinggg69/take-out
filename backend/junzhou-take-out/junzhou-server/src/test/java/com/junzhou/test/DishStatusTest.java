package com.junzhou.test;

import com.junzhou.controller.admin.DishController;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.mapper.DishMapper;
import com.junzhou.service.impl.DishServiceImpl;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishStatusTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        String resource = "mapper/DishMapper.xml";
        try (InputStream xml = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        session.getConnection();

        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table dish (id bigint primary key, name varchar(100), category_id bigint, "
                + "price decimal(10, 2), image varchar(255), description varchar(255), status int, "
                + "create_time timestamp, update_time timestamp, create_user bigint, update_user bigint)");
        jdbc.update("insert into dish values (1, '测试菜品', 2, 18.50, 'image.png', '描述', 1, "
                + "timestamp '2026-01-01 00:00:00', timestamp '2026-01-02 00:00:00', 10, 11)");
        jdbc.update("insert into dish values (2, '其他菜品', 2, 22.00, 'other.png', '其他', 1, "
                + "timestamp '2026-01-01 00:00:00', timestamp '2026-01-02 00:00:00', 10, 11)");

        DishServiceImpl service = new DishServiceImpl();
        ReflectionTestUtils.setField(service, "dishMapper", session.getMapper(DishMapper.class));
        DishController controller = new DishController();
        ReflectionTestUtils.setField(controller, "dishService", service);
        redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.keys("dish_*")).thenReturn(Collections.singleton("dish_2"));
        ReflectionTestUtils.setField(controller, "redisTemplate", redisTemplate);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void stopsAndStartsOnlyTheSelectedDish() throws Exception {
        Map<String, Object> original = row(1);
        Map<String, Object> other = row(2);

        mvc.perform(post("/admin/dish/status/0").param("id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        original.put("STATUS", 0);
        assertEquals(original, row(1));
        assertEquals(other, row(2));

        mvc.perform(post("/admin/dish/status/1").param("id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        original.put("STATUS", 1);
        assertEquals(original, row(1));
        assertEquals(other, row(2));
        verify(redisTemplate, times(2)).delete(Collections.singleton("dish_2"));
    }

    @Test
    void rejectsInvalidStatusAndUnknownDish() throws Exception {
        Map<String, Object> original = row(1);

        mvc.perform(post("/admin/dish/status/2").param("id", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/admin/dish/status/0").param("id", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(original, row(1));
        verifyNoInteractions(redisTemplate);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select * from dish where id = ?", id);
    }
}
