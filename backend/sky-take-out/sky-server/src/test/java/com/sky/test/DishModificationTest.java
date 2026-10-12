package com.sky.test;

import com.sky.constant.MessageConstant;
import com.sky.dto.DishDTO;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.service.impl.DishServiceImpl;
import com.sky.vo.DishVO;
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

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DishModificationTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private DishServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        for (String name : Arrays.asList("DishMapper", "DishFlavorMapper")) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream xml = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        session.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table dish (id bigint primary key, name varchar(100), category_id bigint, "
                + "price decimal(10, 2), image varchar(255), description varchar(255), status int, "
                + "update_time timestamp, update_user bigint)");
        jdbc.execute("create table dish_flavor (id bigint auto_increment primary key, dish_id bigint, "
                + "name varchar(100), `value` varchar(255), foreign key (dish_id) references dish(id))");
        jdbc.update("insert into dish (id, name, category_id, price, status) values (1, '旧菜品', 2, 10.00, 0)");
        jdbc.update("insert into dish_flavor (dish_id, name, `value`) values (1, '辣度', '[\"微辣\"]')");

        service = new DishServiceImpl();
        ReflectionTestUtils.setField(service, "dishMapper", session.getMapper(DishMapper.class));
        ReflectionTestUtils.setField(service, "dishFlavorMapper", session.getMapper(DishFlavorMapper.class));
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void updatesDishAndReplacesFlavors() {
        DishDTO dish = new DishDTO();
        dish.setId(1L);
        dish.setName("新菜品");
        dish.setCategoryId(3L);
        dish.setPrice(new BigDecimal("18.50"));
        dish.setStatus(1);
        dish.setFlavors(Collections.singletonList(
                DishFlavor.builder().name("甜度").value("[\"少糖\"]").build()));

        service.updateWithFlavor(dish);

        DishVO saved = service.getByIdWithFlavor(1L);
        assertEquals("新菜品", saved.getName());
        assertEquals(3L, saved.getCategoryId());
        assertEquals(0, new BigDecimal("18.50").compareTo(saved.getPrice()));
        assertEquals(1, saved.getStatus());
        assertEquals(1, saved.getFlavors().size());
        assertEquals("甜度", saved.getFlavors().get(0).getName());
        assertEquals(1L, saved.getFlavors().get(0).getDishId());
    }

    @Test
    void clearsFlavorsWhenListIsEmpty() {
        DishDTO dish = new DishDTO();
        dish.setId(1L);
        dish.setStatus(0);
        dish.setFlavors(Collections.emptyList());

        service.updateWithFlavor(dish);

        assertEquals(0, service.getByIdWithFlavor(1L).getFlavors().size());
        assertEquals("旧菜品", service.getByIdWithFlavor(1L).getName());
    }

    @Test
    void rejectsMissingIdAndUnknownDishWithoutChangingFlavors() {
        DishDTO missingId = new DishDTO();
        assertEquals(MessageConstant.DISH_ID_REQUIRED,
                assertThrows(BaseException.class, () -> service.updateWithFlavor(missingId)).getMessage());

        DishDTO unknown = new DishDTO();
        unknown.setId(999L);
        assertEquals(MessageConstant.DISH_NOT_EXIST,
                assertThrows(BaseException.class, () -> service.updateWithFlavor(unknown)).getMessage());
        assertEquals(MessageConstant.DISH_NOT_EXIST,
                assertThrows(BaseException.class, () -> service.getByIdWithFlavor(999L)).getMessage());
        assertEquals(1, jdbc.queryForObject("select count(*) from dish_flavor where dish_id = 1", Integer.class));
    }
}
