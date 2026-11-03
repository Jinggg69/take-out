package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.mapper.CategoryMapper;

import com.junzhou.constant.MessageConstant;
import com.junzhou.dto.DishDTO;
import com.junzhou.entity.DishFlavor;
import com.junzhou.exception.BaseException;
import com.junzhou.mapper.DishFlavorMapper;
import com.junzhou.mapper.DishMapper;
import com.junzhou.service.impl.DishServiceImpl;
import com.junzhou.vo.DishVO;
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
        for (String name : Arrays.asList("DishMapper", "DishFlavorMapper", "CategoryMapper")) {
            String resource = "mapper/" + name + ".xml";
            try (InputStream xml = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(xml, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }

        session = new SqlSessionFactoryBuilder().build(configuration).openSession(true);
        session.getConnection();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table dish (id bigint auto_increment primary key, name varchar(100), category_id bigint, "
                + "price decimal(10, 2), image varchar(255), description varchar(255), status int, "
                + "create_time timestamp, create_user bigint, update_time timestamp, update_user bigint)");
        jdbc.execute("create table dish_flavor (id bigint auto_increment primary key, dish_id bigint, "
                + "name varchar(100), `value` varchar(255), foreign key (dish_id) references dish(id))");
        jdbc.update("insert into dish (id, name, category_id, price, status) values (1, '旧菜品', 2, 10.00, 0)");
        jdbc.update("insert into dish_flavor (dish_id, name, `value`) values (1, '辣度', '[\"微辣\"]')");

        RequestScope.set(new RequestScope(42L, null, 1L, 1L, Collections.singleton(1L)));
        jdbc.execute("alter table dish add tenant_id bigint");
        jdbc.update("update dish set tenant_id = 1");
        jdbc.execute("alter table dish alter column tenant_id set not null");
        jdbc.execute("create table category (id bigint primary key, tenant_id bigint not null)");
        jdbc.update("insert into category values (2, 1), (3, 1)");
        service = new DishServiceImpl();
        ReflectionTestUtils.setField(service,"fileAssets",org.mockito.Mockito.mock(com.junzhou.service.FileAssetService.class));
        ReflectionTestUtils.setField(service, "categoryMapper", session.getMapper(CategoryMapper.class));
        ReflectionTestUtils.setField(service, "dishMapper", session.getMapper(DishMapper.class));
        ReflectionTestUtils.setField(service, "dishFlavorMapper", session.getMapper(DishFlavorMapper.class));
    }

    @AfterEach
    void tearDown() {
        RequestScope.clear();
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
    @Test
    void rejectsForeignCategoryAndFlavorWritesWithoutChangingExistingRows() {
        jdbc.update("insert into category values (99, 2)");
        jdbc.update("insert into dish(id,name,category_id,status,tenant_id) values (99,'B',99,0,2)");
        DishDTO update = new DishDTO();
        update.setId(1L); update.setCategoryId(99L); update.setName("forged");
        assertThrows(SecurityException.class, () -> service.updateWithFlavor(update));
        assertEquals("旧菜品", service.getByIdWithFlavor(1L).getName());
        assertEquals(1, service.getByIdWithFlavor(1L).getFlavors().size());
        session.getMapper(DishMapper.class).insertFlavors(Arrays.asList(
                DishFlavor.builder().dishId(1L).name("new A").value("[]").build(),
                DishFlavor.builder().dishId(99L).name("new B").value("[]").build()));
        assertEquals(1, jdbc.queryForObject("select count(*) from dish_flavor", Integer.class));
        assertEquals(0, session.getMapper(DishMapper.class).updateById(
                com.junzhou.entity.Dish.builder().id(99L).name("forged").build()));
    }

    @Test
    void insertsExplicitOwnershipAndMultipleFlavorsAndFailsWithoutScope() {
        DishDTO dto = new DishDTO();
        dto.setName("new dish"); dto.setCategoryId(2L); dto.setStatus(0);
        dto.setFlavors(Arrays.asList(DishFlavor.builder().name("A").value("[]").build(),
                DishFlavor.builder().name("B").value("[]").build()));
        service.saveWithFlavor(dto);
        assertEquals(1L, jdbc.queryForObject("select tenant_id from dish where name='new dish'", Long.class));
        assertEquals(3, jdbc.queryForObject("select count(*) from dish_flavor", Integer.class));
        RequestScope.clear();
        assertThrows(RuntimeException.class, () -> session.getMapper(DishMapper.class).getById(1L));
        assertThrows(RuntimeException.class, () -> session.getMapper(DishFlavorMapper.class).getByDishId(1L));
    }

}
