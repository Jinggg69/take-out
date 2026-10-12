package com.sky.test;

import com.sky.constant.MessageConstant;
import com.sky.controller.admin.DishController;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.service.impl.DishServiceImpl;
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

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DishDeletionTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private DishServiceImpl service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String name : Arrays.asList("DishMapper", "DishFlavorMapper", "SetmealDishMapper")) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream xml = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        session.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table dish (id bigint primary key, status int)");
        jdbc.execute("create table dish_flavor (id bigint primary key, dish_id bigint, "
                + "foreign key (dish_id) references dish(id))");
        jdbc.execute("create table setmeal_dish (setmeal_id bigint, dish_id bigint)");
        jdbc.update("insert into dish values (1, 0), (2, 0), (3, 1), (4, 0), (5, 0)");
        jdbc.update("insert into dish_flavor values (101, 1), (102, 2), (105, 5)");
        jdbc.update("insert into setmeal_dish values (10, 4)");

        service = new DishServiceImpl();
        ReflectionTestUtils.setField(service, "dishMapper", session.getMapper(DishMapper.class));
        ReflectionTestUtils.setField(service, "dishFlavorMapper", session.getMapper(DishFlavorMapper.class));
        ReflectionTestUtils.setField(service, "setmealDishMapper", session.getMapper(SetmealDishMapper.class));
        DishController controller = new DishController();
        ReflectionTestUtils.setField(controller, "dishService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void deletesStoppedDishesAndTheirFlavorsOnly() throws Exception {
        mvc.perform(delete("/admin/dish").param("ids", "1", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        assertEquals(0, count("dish", "id in (1, 2)"));
        assertEquals(0, count("dish_flavor", "dish_id in (1, 2)"));
        assertEquals(1, count("dish", "id = 5"));
        assertEquals(1, count("dish_flavor", "dish_id = 5"));
    }

    @Test
    void rejectsAnOnSaleDishWithoutDeletingOtherItems() {
        DeletionNotAllowedException ex = assertThrows(DeletionNotAllowedException.class,
                () -> service.deleteBatch(Arrays.asList(1L, 3L)));
        assertEquals(MessageConstant.DISH_ON_SALE, ex.getMessage());
        assertEquals(1, count("dish", "id = 1"));
        assertEquals(1, count("dish_flavor", "dish_id = 1"));
    }

    @Test
    void rejectsASetmealDishWithoutDeletingOtherItems() {
        DeletionNotAllowedException ex = assertThrows(DeletionNotAllowedException.class,
                () -> service.deleteBatch(Arrays.asList(1L, 4L)));
        assertEquals(MessageConstant.DISH_BE_RELATED_BY_SETMEAL, ex.getMessage());
        assertEquals(1, count("dish", "id = 1"));
        assertEquals(1, count("dish_flavor", "dish_id = 1"));
    }

    @Test
    void rejectsUnknownAndEmptyIds() {
        DeletionNotAllowedException missing = assertThrows(DeletionNotAllowedException.class,
                () -> service.deleteBatch(Arrays.asList(1L, 999L)));
        assertEquals(MessageConstant.DISH_NOT_FOUND, missing.getMessage());
        DeletionNotAllowedException empty = assertThrows(DeletionNotAllowedException.class,
                () -> service.deleteBatch(Collections.emptyList()));
        assertEquals(MessageConstant.DISH_IDS_EMPTY, empty.getMessage());
        assertEquals(1, count("dish", "id = 1"));
        assertEquals(1, count("dish_flavor", "dish_id = 1"));
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + condition,
                Integer.class);
    }
}
