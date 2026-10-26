package com.sky.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.sky.config.WebMvcConfiguration;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.controller.user.AddressBookController;
import com.sky.entity.AddressBook;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.json.JacksonObjectMapper;
import com.sky.mapper.AddressBookMapper;
import com.sky.properties.JwtProperties;
import com.sky.service.AddressBookService;
import com.sky.service.impl.AddressBookServiceImpl;
import com.sky.utils.JwtUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real controller/service/MyBatis integration against a disposable H2 database.
 * businessMvc supplies an identity to isolate business behavior; webMvc uses the
 * production MVC configuration and signed JWTs to verify the actual login wiring.
 */
class AddressBookModuleTest {
    private static final String API = "/user/addressBook";
    private static final String SECRET = "address-book-test-secret";
    private AnnotationConfigWebApplicationContext context;
    private JdbcTemplate jdbc;
    private AddressBookService service;
    private MockMvc businessMvc;
    private MockMvc webMvc;
    private final JacksonObjectMapper json = new JacksonObjectMapper();

    @BeforeEach
    void setUp() {
        BaseContext.removeCurrentId();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class, AddressBookTestConfiguration.class);
        context.refresh();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        // Match the address_book columns and nullability in database/sky.sql.
        jdbc.execute("create table address_book (id bigint auto_increment primary key, "
                + "user_id bigint not null, consignee varchar(50), sex varchar(2), phone varchar(11) not null, "
                + "province_code varchar(12), province_name varchar(32), city_code varchar(12), city_name varchar(32), "
                + "district_code varchar(12), district_name varchar(32), detail varchar(200), label varchar(100), "
                + "is_default tinyint not null default 0)");
        jdbc.update("insert into address_book (id, user_id, consignee, sex, phone, province_code, province_name, "
                + "city_code, city_name, district_code, district_name, detail, label, is_default) values "
                + "(101, 42, '张三', '1', '13800000001', '310000', '上海市', '310100', '上海市', "
                + "'310105', '长宁区', '测试路1号', '家', 1), "
                + "(102, 42, '李四', '0', '13800000002', '330000', '浙江省', '330100', '杭州市', "
                + "'330106', '西湖区', '测试路2号', '公司', 0), "
                + "(201, 84, '其他用户', '1', '13800000003', '110000', '北京市', '110100', '北京市', "
                + "'110108', '海淀区', '其他用户的地址', '家', 1)");
        service = context.getBean(AddressBookService.class);
        businessMvc = MockMvcBuilders.standaloneSetup(context.getBean(AddressBookController.class))
                .setControllerAdvice(context.getBean(GlobalExceptionHandler.class))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        webMvc = MockMvcBuilders.webAppContextSetup(context).build();
        BaseContext.setCurrentId(42L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        try {
            if (jdbc != null) {
                jdbc.execute("SET DB_CLOSE_DELAY 0");
            }
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    void createsAddressForCurrentUserWithAllFieldsAndIgnoresSpoofedOwnership() throws Exception {
        AddressBook address = newAddress();
        address.setUserId(84L);
        address.setIsDefault(1);
        businessMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(address)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));

        Map<String, Object> saved = jdbc.queryForMap("select * from address_book where phone = '13900000000'");
        assertEquals(42L, ((Number) saved.get("user_id")).longValue());
        assertEquals(0, ((Number) saved.get("is_default")).intValue());
        assertEquals("王五", saved.get("consignee"));
        assertEquals("0", saved.get("sex"));
        assertEquals("440000", saved.get("province_code"));
        assertEquals("广东省", saved.get("province_name"));
        assertEquals("440300", saved.get("city_code"));
        assertEquals("深圳市", saved.get("city_name"));
        assertEquals("440305", saved.get("district_code"));
        assertEquals("南山区", saved.get("district_name"));
        assertEquals("科技路3号", saved.get("detail"));
        assertEquals("学校", saved.get("label"));
    }

    @Test
    void listsOnlyCurrentUsersAddresses() throws Exception {
        businessMvc.perform(get(API + "/list"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].userId", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(42))));
    }

    @Test
    void listsEmptyWhenCurrentUserHasNoAddresses() throws Exception {
        BaseContext.setCurrentId(999L);
        businessMvc.perform(get(API + "/list"))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void getsAddressByIdWithRegionFields() throws Exception {
        businessMvc.perform(get(API + "/101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value("101"))
                .andExpect(jsonPath("$.data.consignee").value("张三"))
                .andExpect(jsonPath("$.data.provinceCode").value("310000"))
                .andExpect(jsonPath("$.data.cityName").value("上海市"))
                .andExpect(jsonPath("$.data.districtName").value("长宁区"));
    }

    @Test
    void updatesContactDetailsWithoutClearingOmittedFields() throws Exception {
        businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content(
                "{\"id\":101,\"consignee\":\"新收货人\",\"phone\":\"13900000001\","
                        + "\"sex\":\"0\",\"detail\":\"新门牌号\",\"label\":\"公司\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        AddressBook saved = service.getById(101L);
        assertEquals("新收货人", saved.getConsignee());
        assertEquals("13900000001", saved.getPhone());
        assertEquals("0", saved.getSex());
        assertEquals("新门牌号", saved.getDetail());
        assertEquals("公司", saved.getLabel());
        assertEquals("上海市", saved.getProvinceName());
        assertEquals(42L, saved.getUserId());
        assertEquals(1, saved.getIsDefault());
    }

    @Test
    void updatesAllRegionFields() throws Exception {
        AddressBook address = newAddress();
        address.setId(101L);
        businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(address)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        AddressBook saved = service.getById(101L);
        assertAll("修改地址必须保存省市区编码和名称",
                () -> assertEquals("440000", saved.getProvinceCode()),
                () -> assertEquals("广东省", saved.getProvinceName()),
                () -> assertEquals("440300", saved.getCityCode()),
                () -> assertEquals("深圳市", saved.getCityName()),
                () -> assertEquals("440305", saved.getDistrictCode()),
                () -> assertEquals("南山区", saved.getDistrictName()));
    }

    @Test
    void setsDefaultForCurrentUserOnly() throws Exception {
        businessMvc.perform(put(API + "/default").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":102}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(0, defaultFlag(101L));
        assertEquals(1, defaultFlag(102L));
        assertEquals(1, defaultFlag(201L));
        businessMvc.perform(get(API + "/default"))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value("102"));
    }

    @Test
    void returnsBusinessErrorWhenNoDefaultExists() throws Exception {
        jdbc.update("update address_book set is_default = 0 where user_id = 42");
        businessMvc.perform(get(API + "/default"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void deletesAddressWithoutAffectingOtherAddresses() throws Exception {
        businessMvc.perform(delete(API).param("id", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(0, addressCount(101L));
        assertEquals(1, addressCount(102L));
        assertEquals(1, addressCount(201L));
        businessMvc.perform(get(API + "/default")).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rollsBackDefaultChangeWhenSecondUpdateFails() {
        jdbc.execute("alter table address_book add constraint reject_new_default "
                + "check (id <> 102 or is_default = 0)");
        AddressBook invalid = new AddressBook();
        invalid.setId(102L);
        assertThrows(RuntimeException.class, () -> service.setDefault(invalid));
        assertEquals(1, defaultFlag(101L));
        assertEquals(0, defaultFlag(102L));
        assertEquals(1, defaultFlag(201L));
        assertEquals("13800000002", service.getById(102L).getPhone());
    }

    @Test
    void combinesUserPhoneAndDefaultFilters() {
        AddressBook filter = new AddressBook();
        filter.setUserId(42L);
        filter.setPhone("13800000001");
        filter.setIsDefault(1);
        List<AddressBook> matches = service.list(filter);
        assertEquals(1, matches.size());
        assertEquals(101L, matches.get(0).getId());
        filter.setIsDefault(0);
        assertTrue(service.list(filter).isEmpty());
    }

    @Test
    void rejectsReadingAnotherUsersAddress() throws Exception {
        assertRejected(businessMvc.perform(get(API + "/201")).andReturn());
    }

    @Test
    void rejectsUpdatingAnotherUsersAddress() throws Exception {
        MvcResult result = businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":201,\"detail\":\"越权修改\"}")).andReturn();
        assertAll("用户42不能修改用户84的地址",
                () -> assertRejected(result),
                () -> assertEquals("其他用户的地址", jdbc.queryForObject(
                        "select detail from address_book where id = 201", String.class)));
    }

    @Test
    void rejectsDeletingAnotherUsersAddress() throws Exception {
        MvcResult result = businessMvc.perform(delete(API).param("id", "201")).andReturn();
        assertAll("用户42不能删除用户84的地址",
                () -> assertRejected(result),
                () -> assertEquals(1, addressCount(201L)));
    }

    @Test
    void rejectsSettingAnotherUsersAddressAsDefault() throws Exception {
        jdbc.update("update address_book set is_default = 0 where id = 201");
        MvcResult result = businessMvc.perform(put(API + "/default").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":201}")).andReturn();
        assertAll("越权设置默认地址必须被拒绝且不能改变任何用户的默认地址",
                () -> assertRejected(result),
                () -> assertEquals(1, defaultFlag(101L)),
                () -> assertEquals(0, defaultFlag(201L)));
    }

    @Test
    void unknownDefaultAddressIsRejectedWithoutClearingExistingDefault() throws Exception {
        MvcResult result = businessMvc.perform(put(API + "/default").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999}")).andReturn();
        assertAll("不存在的目标地址不能导致原默认地址丢失",
                () -> assertRejected(result),
                () -> assertEquals(1, defaultFlag(101L)),
                () -> assertEquals(0, defaultFlag(102L)),
                () -> assertEquals(1, defaultFlag(201L)));
    }

    @Test
    void ordinaryUpdateCannotCreateMultipleDefaults() throws Exception {
        businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":102,\"isDefault\":1}")).andReturn();
        assertEquals(1, jdbc.queryForObject(
                "select count(*) from address_book where user_id = 42 and is_default = 1", Integer.class),
                "普通修改接口也必须保持每个用户最多一个默认地址");
    }

    @Test
    void ordinaryUpdateIgnoresSpoofedOwnerAndDefaultFlag() throws Exception {
        businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content(
                "{\"id\":101,\"userId\":84,\"isDefault\":0,\"detail\":\"新地址\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        AddressBook saved = service.getById(101L);
        assertEquals(42L, saved.getUserId());
        assertEquals(1, saved.getIsDefault());
        assertEquals("新地址", saved.getDetail());
        assertEquals(1, defaultFlag(201L));
    }

    @Test
    void ordinaryUpdateWithOnlyProtectedFieldsIsASuccessfulNoOp() throws Exception {
        businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":102,\"userId\":84,\"isDefault\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(0, defaultFlag(102L));
        assertEquals(1, defaultFlag(101L));
        assertEquals(42L, jdbc.queryForObject(
                "select user_id from address_book where id = 102", Long.class));
    }

    @Test
    void settingDefaultDoesNotEditContactOrRegionFields() throws Exception {
        businessMvc.perform(put(API + "/default").contentType(MediaType.APPLICATION_JSON).content(
                "{\"id\":102,\"userId\":84,\"phone\":\"13900000000\",\"cityName\":\"深圳市\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        AddressBook saved = service.getById(102L);
        assertEquals("13800000002", saved.getPhone());
        assertEquals("杭州市", saved.getCityName());
        assertEquals(42L, saved.getUserId());
        assertEquals(1, saved.getIsDefault());
        assertEquals(0, defaultFlag(101L));
        assertEquals(1, defaultFlag(201L));
    }

    @Test
    void signedRequestsCannotAccessAnotherUsersAddress() throws Exception {
        BaseContext.removeCurrentId();
        webMvc.perform(get(API + "/201").header("authentication", token(SECRET, 60000)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertNull(BaseContext.getCurrentId());
        webMvc.perform(put(API).header("authentication", token(SECRET, 60000))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":201,\"userId\":42,\"detail\":\"越权修改\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertNull(BaseContext.getCurrentId());
        assertEquals("其他用户的地址", jdbc.queryForObject(
                "select detail from address_book where id = 201", String.class));
    }

    @Test
    void missingAddressCannotBeReadUpdatedOrDeleted() throws Exception {
        businessMvc.perform(get(API + "/999"))
                .andExpect(jsonPath("$.code").value(0));
        businessMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":999,\"detail\":\"不存在的地址\"}"))
                .andExpect(jsonPath("$.code").value(0));
        businessMvc.perform(delete(API).param("id", "999"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, defaultFlag(101L));
        assertEquals(1, defaultFlag(201L));
    }

    @Test
    void anonymousListIsRejected() throws Exception {
        BaseContext.removeCurrentId();
        webMvc.perform(get(API + "/list")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousDeleteIsRejectedWithoutDeletingData() throws Exception {
        BaseContext.removeCurrentId();
        MvcResult result = webMvc.perform(delete(API).param("id", "201")).andReturn();
        assertAll("未登录不能删除地址",
                () -> assertEquals(401, result.getResponse().getStatus()),
                () -> assertEquals(1, addressCount(201L)));
    }

    @Test
    void signedTokenRestrictsListToTokenOwner() throws Exception {
        BaseContext.removeCurrentId();
        webMvc.perform(get(API + "/list").header("authentication", token(SECRET, 60000)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].userId", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is(42))));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void signedTokenPopulatesUserWhenCreatingAddress() throws Exception {
        BaseContext.removeCurrentId();
        MvcResult result = assertDoesNotThrow(() -> webMvc.perform(post(API)
                .header("authentication", token(SECRET, 60000)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(newAddress()))).andReturn(),
                "合法令牌新增地址应获得当前用户ID并成功保存");
        assertEquals(200, result.getResponse().getStatus());
        assertEquals(1, json.readTree(result.getResponse().getContentAsByteArray()).path("code").asInt());
        assertEquals(42L, jdbc.queryForObject(
                "select user_id from address_book where phone = '13900000000'", Long.class));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        BaseContext.removeCurrentId();
        webMvc.perform(get(API + "/list").header("authentication", token(SECRET, -60000)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenSignatureIsRejected() throws Exception {
        BaseContext.removeCurrentId();
        webMvc.perform(get(API + "/list").header("authentication", token("wrong-secret", 60000)))
                .andExpect(status().isUnauthorized());
    }

    private AddressBook newAddress() {
        return AddressBook.builder().consignee("王五").phone("13900000000").sex("0")
                .provinceCode("440000").provinceName("广东省").cityCode("440300").cityName("深圳市")
                .districtCode("440305").districtName("南山区").detail("科技路3号").label("学校").build();
    }

    private int defaultFlag(long id) {
        return jdbc.queryForObject("select is_default from address_book where id = ?", Integer.class, id);
    }

    private int addressCount(long id) {
        return jdbc.queryForObject("select count(*) from address_book where id = ?", Integer.class, id);
    }

    private String token(String secret, long ttl) {
        return JwtUtil.createJWT(secret, ttl,
                new HashMap<>(Collections.singletonMap(JwtClaimsConstant.USER_ID, 42L)));
    }

    private void assertRejected(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status >= 400 && status < 500) {
            return;
        }
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(status < 300 && body.has("code") && body.get("code").asInt() == 0,
                "应拒绝操作，实际 HTTP " + status + ": " + body);
    }

    @TestConfiguration
    @EnableTransactionManagement
    static class AddressBookTestConfiguration {
        @Bean
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:address_book_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
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
            factory.setTypeAliasesPackage("com.sky.entity");
            org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(config);
            factory.setMapperLocations(new ClassPathResource("mapper/AddressBookMapper.xml"));
            return factory.getObject();
        }

        @Bean
        AddressBookMapper addressBookMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(AddressBookMapper.class);
        }

        @Bean
        AddressBookService addressBookService() {
            return new AddressBookServiceImpl();
        }

        @Bean
        AddressBookController addressBookController() {
            return new AddressBookController();
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        JwtTokenUserInterceptor jwtTokenUserInterceptor() {
            return new JwtTokenUserInterceptor();
        }

        @Bean
        JwtTokenAdminInterceptor jwtTokenAdminInterceptor() {
            return new JwtTokenAdminInterceptor();
        }

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setUserSecretKey(SECRET);
            properties.setUserTokenName("authentication");
            return properties;
        }
    }
}
