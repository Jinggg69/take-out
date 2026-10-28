package com.junzhou.test;

import com.github.pagehelper.PageInterceptor;
import com.junzhou.aspect.AutoFillAspect;
import com.junzhou.constant.MessageConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.controller.admin.DishController;
import com.junzhou.controller.admin.SetmealController;
import com.junzhou.dto.SetmealDTO;
import com.junzhou.entity.SetmealDish;
import com.junzhou.exception.BaseException;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.json.JacksonObjectMapper;
import com.junzhou.mapper.DishMapper;
import com.junzhou.mapper.SetmealDishMapper;
import com.junzhou.mapper.SetmealMapper;
import com.junzhou.service.DishService;
import com.junzhou.service.SetmealService;
import com.junzhou.service.impl.DishServiceImpl;
import com.junzhou.service.impl.SetmealServiceImpl;
import com.junzhou.vo.SetmealVO;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SetmealManagementTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SetmealService service;
    private MockMvc mvc;
    private final JacksonObjectMapper objectMapper = new JacksonObjectMapper();

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("create table category (id bigint primary key, name varchar(100))");
        jdbc.execute("create table dish (id bigint primary key, name varchar(100), category_id bigint, "
                + "price decimal(10, 2), status int, create_time timestamp)");
        jdbc.execute("create table setmeal (id bigint auto_increment primary key, category_id bigint not null, "
                + "name varchar(100) not null unique, price decimal(10, 2) not null, status int, "
                + "description varchar(255), image varchar(255), create_time timestamp not null, "
                + "update_time timestamp not null, create_user bigint not null, update_user bigint not null)");
        // 份数上限仅用于测试：让关联写入在校验之后失败，以验证真实事务回滚。
        jdbc.execute("create table setmeal_dish (id bigint auto_increment primary key, "
                + "setmeal_id bigint references setmeal(id), dish_id bigint references dish(id), "
                + "name varchar(100), price decimal(10, 2), copies int check (copies <= 10))");
        jdbc.update("insert into category values (10, '套餐分类'), (20, '其他分类')");
        jdbc.update("insert into dish (id, name, category_id, price, status, create_time) values "
                + "(1, '在售菜品', 30, 18.50, 1, current_timestamp), "
                + "(2, '停售菜品', 30, 12.00, 0, current_timestamp), "
                + "(3, '其他菜品', 40, 20.00, 1, current_timestamp)");
        service = context.getBean(SetmealService.class);
        SetmealController setmealController = new SetmealController();
        ReflectionTestUtils.setField(setmealController, "setmealService", service);
        DishController dishController = new DishController();
        ReflectionTestUtils.setField(dishController, "dishService", context.getBean(DishService.class));
        mvc = MockMvcBuilders.standaloneSetup(setmealController, dishController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper)).build();
        BaseContext.setCurrentId(42L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void createsSetmealAndReturnsDishesWithAuditFields() throws Exception {
        SetmealDTO dto = dto("测试套餐", 1L);
        // 客户端带来的关系ID、套餐ID及冗余信息不能覆盖数据库中的实际关联。
        dto.getSetmealDishes().get(0).setId(999L);
        dto.getSetmealDishes().get(0).setSetmealId(999L);
        dto.getSetmealDishes().get(0).setName("错误名称");
        dto.getSetmealDishes().get(0).setPrice(BigDecimal.ZERO);
        mvc.perform(post("/admin/setmeal").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        long id = idByName("测试套餐");
        mvc.perform(get("/admin/setmeal/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.categoryName").value("套餐分类"))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.setmealDishes[0].setmealId").value(id))
                .andExpect(jsonPath("$.data.setmealDishes[0].dishId").value(1))
                .andExpect(jsonPath("$.data.setmealDishes[0].name").value("在售菜品"))
                .andExpect(jsonPath("$.data.setmealDishes[0].price").value(18.50));
        Map<String, Object> row = row(id);
        assertNotNull(row.get("CREATE_TIME"));
        assertEquals(row.get("CREATE_TIME"), row.get("UPDATE_TIME"));
        assertEquals(42L, ((Number) row.get("CREATE_USER")).longValue());
        assertEquals(42L, ((Number) row.get("UPDATE_USER")).longValue());
    }

    @Test
    void paginatesAndCombinesNameCategoryAndStatusFilters() throws Exception {
        service.saveWithDish(dto("午餐A", 1L));
        service.saveWithDish(dto("午餐B", 1L));
        SetmealDTO other = dto("午餐其他分类", 1L);
        other.setCategoryId(20L);
        service.saveWithDish(other);
        SetmealDTO onSale = dto("午餐在售", 1L);
        onSale.setStatus(1);
        service.saveWithDish(onSale);
        service.saveWithDish(dto("晚餐", 1L));

        mvc.perform(get("/admin/setmeal/page").param("page", "2").param("pageSize", "1")
                        .param("name", "午餐").param("categoryId", "10").param("status", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("午餐A"))
                .andExpect(jsonPath("$.data.records[0].categoryName").value("套餐分类"));
        mvc.perform(get("/admin/setmeal/page").param("page", "1").param("pageSize", "10"))
                .andExpect(jsonPath("$.data.total").value(5));
    }

    @Test
    void updatesSetmealAndReplacesDishesWithoutChangingCreationFields() throws Exception {
        service.saveWithDish(dto("旧套餐", 1L));
        long id = idByName("旧套餐");
        Map<String, Object> original = row(id);
        SetmealDTO update = dto("新套餐", 3L);
        update.setId(id);
        update.setCategoryId(20L);
        update.setPrice(new BigDecimal("38.00"));
        update.getSetmealDishes().get(0).setCopies(2);
        BaseContext.setCurrentId(43L);

        mvc.perform(put("/admin/setmeal").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        SetmealVO saved = service.getByIdWithDish(id);
        assertEquals("新套餐", saved.getName());
        assertEquals("其他分类", saved.getCategoryName());
        assertEquals(0, new BigDecimal("38.00").compareTo(saved.getPrice()));
        assertEquals(0, saved.getStatus());
        assertEquals(1, saved.getSetmealDishes().size());
        assertEquals(3L, saved.getSetmealDishes().get(0).getDishId());
        assertEquals(2, saved.getSetmealDishes().get(0).getCopies());
        assertEquals(original.get("CREATE_TIME"), row(id).get("CREATE_TIME"));
        assertEquals(original.get("CREATE_USER"), row(id).get("CREATE_USER"));
        assertEquals(43L, ((Number) row(id).get("UPDATE_USER")).longValue());
    }

    @Test
    void deletesOnlySelectedStoppedSetmealsAndTheirRelations() throws Exception {
        service.saveWithDish(dto("删除A", 1L));
        service.saveWithDish(dto("删除B", 3L));
        service.saveWithDish(dto("保留", 1L));
        long first = idByName("删除A");
        long second = idByName("删除B");
        mvc.perform(delete("/admin/setmeal").param("ids", first + "," + second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(1, count("setmeal"));
        assertEquals(1, count("setmeal_dish"));
        assertEquals("保留", service.getByIdWithDish(idByName("保留")).getName());
        assertEquals(3, count("dish"));
    }

    @Test
    void rejectsOnSaleMissingAndEmptyDeletionWithoutPartialChanges() {
        service.saveWithDish(dto("停售套餐", 1L));
        SetmealDTO onSale = dto("起售套餐", 1L);
        onSale.setStatus(1);
        service.saveWithDish(onSale);
        List<Long> ids = Arrays.asList(idByName("停售套餐"), idByName("起售套餐"));
        assertEquals(MessageConstant.SETMEAL_ON_SALE,
                assertThrows(BaseException.class, () -> service.deleteBatch(ids)).getMessage());
        assertThrows(BaseException.class, () -> service.deleteBatch(Arrays.asList(ids.get(0), 999L)));
        assertThrows(BaseException.class, () -> service.deleteBatch(Collections.emptyList()));
        assertEquals(2, count("setmeal"));
        assertEquals(2, count("setmeal_dish"));
    }

    @Test
    void startsAndStopsSelectedSetmealAndChecksCurrentDishStatus() throws Exception {
        service.saveWithDish(dto("状态套餐", 1L));
        long id = idByName("状态套餐");
        mvc.perform(post("/admin/setmeal/status/1").param("id", String.valueOf(id)))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(1, row(id).get("STATUS"));
        mvc.perform(post("/admin/setmeal/status/0").param("id", String.valueOf(id)))
                .andExpect(jsonPath("$.code").value(1));
        jdbc.update("update dish set status = 0 where id = 1");
        mvc.perform(post("/admin/setmeal/status/1").param("id", String.valueOf(id)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value(MessageConstant.SETMEAL_ENABLE_FAILED));
        assertEquals(0, row(id).get("STATUS"));
        mvc.perform(post("/admin/setmeal/status/2").param("id", String.valueOf(id)))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/admin/setmeal/status/0").param("id", "999"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/admin/setmeal/999")).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void preventsSaveAndUpdateFromEnablingStoppedDishes() {
        SetmealDTO invalid = dto("非法起售", 2L);
        invalid.setStatus(1);
        assertEquals(MessageConstant.SETMEAL_ENABLE_FAILED,
                assertThrows(BaseException.class, () -> service.saveWithDish(invalid)).getMessage());
        assertEquals(0, count("setmeal"));

        SetmealDTO onSale = dto("原起售套餐", 1L);
        onSale.setStatus(1);
        service.saveWithDish(onSale);
        long id = idByName("原起售套餐");
        SetmealDTO update = dto("替换成停售菜品", 2L);
        update.setId(id);
        // 修改不传状态时保留原状态，同样要校验新增的菜品。
        assertThrows(BaseException.class, () -> service.updateWithDish(update));
        assertEquals("原起售套餐", service.getByIdWithDish(id).getName());
        assertEquals(1L, service.getByIdWithDish(id).getSetmealDishes().get(0).getDishId());
    }

    @Test
    void rejectsEmptyUnknownAndInvalidDishRelationsBeforeChangingData() {
        SetmealDTO empty = dto("空套餐", 1L);
        empty.setSetmealDishes(Collections.emptyList());
        assertThrows(BaseException.class, () -> service.saveWithDish(empty));
        SetmealDTO missing = dto("不存在菜品", 999L);
        assertThrows(BaseException.class, () -> service.saveWithDish(missing));
        SetmealDTO invalid = dto("零份套餐", 1L);
        invalid.getSetmealDishes().get(0).setCopies(0);
        assertThrows(BaseException.class, () -> service.saveWithDish(invalid));
        assertEquals(0, count("setmeal"));
        assertEquals(0, count("setmeal_dish"));
    }

    @Test
    void rollsBackBothTablesWhenRelationInsertFails() {
        SetmealDTO failedSave = dto("保存回滚", 1L);
        failedSave.getSetmealDishes().get(0).setCopies(11);
        assertThrows(RuntimeException.class, () -> service.saveWithDish(failedSave));
        assertEquals(0, count("setmeal"));
        assertEquals(0, count("setmeal_dish"));

        service.saveWithDish(dto("原套餐", 1L));
        long id = idByName("原套餐");
        Map<String, Object> original = row(id);
        SetmealDTO failedUpdate = dto("更新回滚", 3L);
        failedUpdate.setId(id);
        failedUpdate.getSetmealDishes().get(0).setCopies(11);
        assertThrows(RuntimeException.class, () -> service.updateWithDish(failedUpdate));
        assertEquals(original, row(id));
        assertEquals(1, count("setmeal_dish"));
        assertEquals(1L, service.getByIdWithDish(id).getSetmealDishes().get(0).getDishId());
    }

    @Test
    void listsOnlyOnSaleDishesInSelectedCategory() throws Exception {
        mvc.perform(get("/admin/dish/list").param("categoryId", "30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1));
        mvc.perform(get("/admin/dish/list").param("categoryId", "999"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private SetmealDTO dto(String name, Long dishId) {
        SetmealDTO dto = new SetmealDTO();
        dto.setName(name);
        dto.setCategoryId(10L);
        dto.setPrice(new BigDecimal("28.00"));
        dto.setImage("setmeal.png");
        dto.setDescription("套餐描述");
        dto.setSetmealDishes(Collections.singletonList(
                SetmealDish.builder().dishId(dishId).copies(1).build()));
        return dto;
    }

    private long idByName(String name) {
        return jdbc.queryForObject("select id from setmeal where name = ?", Long.class, name);
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Map<String, Object> row(long id) {
        return jdbc.queryForMap("select * from setmeal where id = ?", id);
    }

    @Configuration
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            return dataSource;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            factory.setMapperLocations(new ClassPathResource("mapper/SetmealMapper.xml"),
                    new ClassPathResource("mapper/SetmealDishMapper.xml"),
                    new ClassPathResource("mapper/DishMapper.xml"));
            PageInterceptor pageInterceptor = new PageInterceptor();
            Properties properties = new Properties();
            properties.setProperty("helperDialect", "mysql");
            pageInterceptor.setProperties(properties);
            factory.setPlugins(pageInterceptor);
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        SetmealMapper setmealMapper(SqlSessionTemplate template) {
            return template.getMapper(SetmealMapper.class);
        }

        @Bean
        SetmealDishMapper setmealDishMapper(SqlSessionTemplate template) {
            return template.getMapper(SetmealDishMapper.class);
        }

        @Bean
        DishMapper dishMapper(SqlSessionTemplate template) {
            return template.getMapper(DishMapper.class);
        }

        @Bean
        SetmealService setmealService() {
            return new SetmealServiceImpl();
        }

        @Bean
        DishService dishService() {
            return new DishServiceImpl();
        }

        @Bean
        com.junzhou.mapper.DishFlavorMapper dishFlavorMapper() {
            return org.mockito.Mockito.mock(com.junzhou.mapper.DishFlavorMapper.class);
        }

        @Bean
        AutoFillAspect autoFillAspect() {
            return new AutoFillAspect();
        }
    }
}
