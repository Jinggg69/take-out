package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.*;
import com.junzhou.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real MySQL verification; creates and drops only a random test-owned schema. */
@EnabledIfEnvironmentVariable(named = "CATALOG_MYSQL_TEST", matches = "true")
class CatalogMySqlIsolationTest {
    @Test void executesActualMapperSqlAgainstDisposableMysqlSchema() throws Exception {
        String schema = "catalog_verify_" + UUID.randomUUID().toString().replace("-", "");
        String port = System.getenv("CATALOG_MYSQL_PORT");
        assertTrue(port.matches("[0-9]+"));
        String base = "jdbc:mysql://127.0.0.1:" + port + "/";
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        String user = System.getenv("CATALOG_MYSQL_USER"), password = System.getenv("CATALOG_MYSQL_PASSWORD");
        DriverManagerDataSource admin = new DriverManagerDataSource(base + options, user, password);
        JdbcTemplate root = new JdbcTemplate(admin);
        root.execute("create database `" + schema + "`");
        try {
            DriverManagerDataSource ds = new DriverManagerDataSource(base + schema + options, user, password);
            JdbcTemplate db = new JdbcTemplate(ds);
            db.execute("create table category(id bigint primary key, tenant_id bigint not null, name varchar(100))");
            String columns = "(id bigint auto_increment primary key, tenant_id bigint not null, category_id bigint, name varchar(100), price decimal(10,2), status int, image varchar(255), description varchar(255), create_time datetime, update_time datetime, create_user bigint, update_user bigint)";
            db.execute("create table dish " + columns);
            db.execute("create table setmeal " + columns);
            db.execute("create table dish_flavor(id bigint auto_increment primary key, dish_id bigint, name varchar(100), `value` varchar(255))");
            db.execute("create table setmeal_dish(id bigint auto_increment primary key, setmeal_id bigint, dish_id bigint, name varchar(100), price decimal(10,2), copies int)");
            db.update("insert into category values(1,1,'A'),(2,2,'B')");
            db.update("insert into dish(id,tenant_id,category_id,name,status) values(1,1,1,'A1',0),(2,1,1,'A2',0),(3,2,2,'B1',0)");
            db.update("insert into setmeal(id,tenant_id,category_id,name,status) values(1,1,1,'A',0),(2,2,2,'B',0)");
            SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(ds);
            // Standalone sessions need a JDBC transaction; Spring-managed sessions
            // outside a Spring transaction otherwise keep JDBC auto-commit enabled.
            bean.setTransactionFactory(new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory());
            bean.setMapperLocations(new ClassPathResource("mapper/CategoryMapper.xml"), new ClassPathResource("mapper/DishMapper.xml"), new ClassPathResource("mapper/DishFlavorMapper.xml"), new ClassPathResource("mapper/SetmealMapper.xml"), new ClassPathResource("mapper/SetmealDishMapper.xml"));
            RequestScope.set(new RequestScope(1L,null,1L,1L,Collections.singleton(1L)));
            try (SqlSession session = bean.getObject().openSession(true)) {
                DishMapper dishes = session.getMapper(DishMapper.class);
                DishFlavorMapper flavors = session.getMapper(DishFlavorMapper.class);
                SetmealMapper meals = session.getMapper(SetmealMapper.class);
                SetmealDishMapper relations = session.getMapper(SetmealDishMapper.class);
                assertNull(dishes.getById(3L));
                assertNull(meals.getById(2L));
                assertEquals(0, dishes.updateById(Dish.builder().id(1L).categoryId(2L).build()));
                assertEquals(0, meals.updateById(Setmeal.builder().id(1L).categoryId(2L).build()));
                Dish added = Dish.builder().name("created").categoryId(1L).build();
                assertEquals(1, dishes.insert(added));
                assertEquals(1L, db.queryForObject("select tenant_id from dish where id=?",Long.class,added.getId()));
                assertEquals(2, dishes.insertFlavors(Arrays.asList(DishFlavor.builder().dishId(1L).name("a").value("[]").build(),DishFlavor.builder().dishId(2L).name("b").value("[]").build())));
                assertEquals(0, dishes.insertFlavors(Arrays.asList(DishFlavor.builder().dishId(1L).name("a").value("[]").build(),DishFlavor.builder().dishId(3L).name("b").value("[]").build())));
                assertEquals(2, relations.insertBatch(Arrays.asList(component(1L,1L),component(1L,2L))));
                assertEquals(0, relations.insertBatch(Arrays.asList(component(1L,1L),component(2L,3L))));
                relations.deleteBySetmealIds(Arrays.asList(1L,2L));
                meals.deleteBatch(Arrays.asList(1L,2L));
                flavors.deleteByDishIds(Arrays.asList(1L,3L));
                dishes.deleteBatch(Arrays.asList(1L,3L));
                assertEquals(2, db.queryForObject("select count(*) from setmeal",Integer.class));
                assertEquals(2, db.queryForObject("select count(*) from setmeal_dish",Integer.class));
                assertEquals(2, db.queryForObject("select count(*) from dish_flavor",Integer.class));
                assertEquals(4, db.queryForObject("select count(*) from dish",Integer.class));
                relations.deleteBySetmealIds(Collections.singletonList(1L));
                meals.deleteBatch(Collections.singletonList(1L));
                flavors.deleteByDishIds(Arrays.asList(1L,2L));
                dishes.deleteBatch(Arrays.asList(1L,2L));
                assertEquals(0, db.queryForObject("select count(*) from setmeal_dish",Integer.class));
                assertEquals(1, db.queryForObject("select count(*) from setmeal",Integer.class));
                assertEquals(0, db.queryForObject("select count(*) from dish_flavor",Integer.class));
                assertEquals(2, db.queryForObject("select count(*) from dish",Integer.class));
            }
            // The same category row lock used by catalog writes blocks concurrent deletion.
            try (SqlSession locked = bean.getObject().openSession(false);
                 java.sql.Connection competing = ds.getConnection();
                 java.sql.Statement statement = competing.createStatement()) {
                assertEquals(1L, locked.getMapper(CategoryMapper.class).lockOwned(1L, 1L));
                statement.execute("set innodb_lock_wait_timeout=1");
                java.sql.SQLException timeout = assertThrows(java.sql.SQLException.class,
                        () -> statement.executeUpdate("delete from category where id=1 and tenant_id=1"));
                assertEquals(1205, timeout.getErrorCode());
                locked.rollback();
            }
        } finally {
            RequestScope.clear();
            assertTrue(schema.matches("catalog_verify_[a-f0-9]{32}"));
            root.execute("drop database `" + schema + "`");
        }
    }
    private SetmealDish component(Long meal, Long dish) {
        return SetmealDish.builder().setmealId(meal).dishId(dish).name("item").price(BigDecimal.ONE).copies(1).build();
    }
}
