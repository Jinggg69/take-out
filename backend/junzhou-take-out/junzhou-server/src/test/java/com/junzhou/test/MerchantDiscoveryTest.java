package com.junzhou.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junzhou.context.RequestScope;
import com.junzhou.controller.user.MerchantDiscoveryController;
import com.junzhou.controller.user.StoreContextController;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.mapper.MerchantDiscoveryMapper;
import com.junzhou.service.MerchantDiscoveryService;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MerchantDiscoveryTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private MockMvc mvc;
    private RedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        RequestScope.clear();
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(db);
        jdbc.execute("create table tenant(id bigint primary key,name varchar(100),status int,logo_url varchar(512),introduction varchar(500))");
        jdbc.execute("create table store(id bigint primary key,tenant_id bigint,name varchar(100),status int)");
        jdbc.execute("create table store_settings(tenant_id bigint,store_id bigint,version bigint,address varchar(255),contact_phone varchar(32),latitude decimal(9,6),longitude decimal(9,6),opens_at varchar(5),closes_at varchar(5),delivery_radius_meters int,minimum_order_amount decimal(10,2),pack_fee_per_item decimal(10,2),delivery_fee decimal(10,2),free_delivery_threshold decimal(10,2),primary key(tenant_id,store_id))");
        jdbc.update("insert into tenant values (9007199254740993,'鱼庄%_!',1,'https://cdn.example.com/public/logo.png','介绍'),(2,'商家乙',1,'/admin/common/download?id=2',null),(3,'停用商家',0,null,null),(4,'空商家',1,null,null)");
        jdbc.update("insert into store values (11,9007199254740993,'一店%_!',1),(12,9007199254740993,'二店',1),(13,9007199254740993,'停用店',0),(21,2,'乙店',1),(31,3,'隐藏店',1)");
        jdbc.update("insert into store_settings values(9007199254740993,11,7,'门店地址','0719-1234567',32.500000,111.500000,'00:00','00:00',5000,20,1.5,6,null)");
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(db);
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true); config.addMapper(MerchantDiscoveryMapper.class); bean.setConfiguration(config);
        session = bean.getObject().openSession(true);
        redis = mock(RedisTemplate.class, RETURNS_DEEP_STUBS);
        when(redis.opsForValue().get(anyString())).thenReturn(null);
        MerchantDiscoveryService service = new MerchantDiscoveryService(session.getMapper(MerchantDiscoveryMapper.class), redis);
        mvc = MockMvcBuilders.standaloneSetup(new MerchantDiscoveryController(service),new StoreContextController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void close() { session.close(); RequestScope.clear(); }
    private JsonNode data(String path) throws Exception {
        String body = mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data");
    }
    @Test void publicDirectoryUsesOnlyActiveMerchantsWithActiveStoresAndStablePagination() throws Exception {
        JsonNode page = data("/user/merchants?page=1&pageSize=1");
        assertEquals(2,page.get("total").asInt()); assertEquals("2",page.at("/records/0/merchantId").asText());
        assertTrue(page.at("/records/0/logoUrl").isNull());
        assertEquals("9007199254740993",data("/user/merchants?page=2&pageSize=1").at("/records/0/merchantId").asText());
        assertEquals(0,data("/user/merchants?page=3&pageSize=1").get("records").size());
        assertNull(RequestScope.current());
    }
    @Test void keywordIsTrimmedAndPercentUnderscoreEscapeAreLiteral() throws Exception {
        for(String literal:Arrays.asList("%","_","!","%_!")) {
            mvc.perform(get("/user/merchants").param("keyword",literal)).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/user/merchants/9007199254740993/stores").param("keyword",literal)).andExpect(jsonPath("$.data.total").value(1));
        }
        mvc.perform(get("/user/merchants").param("keyword","  鱼庄%_!  ")).andExpect(jsonPath("$.data.total").value(1));
        assertEquals(0,data("/user/merchants?keyword=无结果").get("total").asInt());
    }
    @Test void storesStayWithinPathMerchantAndRestingStoresRemainVisible() throws Exception {
        when(redis.opsForValue().get("junzhou:v2:tenant:9007199254740993:store:11:shop-status")).thenReturn(0);
        JsonNode page = data("/user/merchants/9007199254740993/stores");
        assertEquals(2,page.get("total").asInt()); assertEquals("11",page.at("/records/0/storeId").asText());
        assertFalse(page.at("/records/0/open").asBoolean()); assertEquals("12",page.at("/records/1/storeId").asText());
        assertEquals("9007199254740993",page.at("/records/1/merchantId").asText());
    }
    @Test void contextHasExactPublicWhitelistAndStringMoneyAndIds() throws Exception {
        JsonNode context = data("/user/stores/11/context");
        Set<String> fields = new HashSet<>(); context.fieldNames().forEachRemaining(fields::add);
        assertEquals(new HashSet<>(Arrays.asList("merchantId","merchantName","logoUrl","storeId","storeName","address","contactPhone","latitude","longitude","coordinateSystem","open","opensAt","closesAt","minimumOrderAmount","packFeePerItem","deliveryFee","freeDeliveryThreshold","deliveryRadiusMeters","configVersion")),fields);
        assertEquals("9007199254740993",context.get("merchantId").textValue()); assertEquals("11",context.get("storeId").textValue());
        assertEquals("7",context.get("configVersion").textValue()); assertEquals("BD09",context.get("coordinateSystem").textValue());
        assertEquals("20.00",context.get("minimumOrderAmount").textValue()); assertEquals("1.50",context.get("packFeePerItem").textValue());
        assertEquals("6.00",context.get("deliveryFee").textValue()); assertTrue(context.get("freeDeliveryThreshold").isNull());
        assertEquals("0719-1234567",context.get("contactPhone").textValue()); assertTrue(context.get("open").asBoolean());
        assertEquals("00:00",data("/user/stores/12/context").get("opensAt").textValue());
    }
    @Test void merchantWhitelistAndUnavailableResources() throws Exception {
        JsonNode merchant = data("/user/merchants/9007199254740993");
        assertEquals(5,merchant.size()); assertEquals(2,merchant.get("activeStoreCount").asInt());
        for(String path:Arrays.asList("/user/merchants/3","/user/merchants/4","/user/merchants/99/stores","/user/stores/13/context","/user/stores/31/context","/user/stores/99/context"))
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(0));
    }
    @Test void employeeAuthenticatedAndMalformedLogoLocationsNeverReachPublicDtos() throws Exception {
        for(String url:Arrays.asList("https://api.example.com/admin/common/file?url=x","https://api.example.com/%61dmin/common/file","https://api.example.com/public/../admin/common/file","javascript:alert(1)","/admin/common/file",
                "https://api.example.com/api/file/2","https://cdn.example.com/public/logo.png?token=secret","https://cdn.example.com/public/logo.png#secret","https://name:secret@cdn.example.com/public/logo.png")) {
            jdbc.update("update tenant set logo_url=? where id=9007199254740993",url);
            // Avoid the raw SqlSession first-level cache when modifying through JdbcTemplate.
            session.clearCache();
            assertTrue(data("/user/merchants/9007199254740993").get("logoUrl").isNull());
            assertTrue(data("/user/stores/11/context").get("logoUrl").isNull());
        }
    }
    @Test void malformedIdsAndPaginationReturnJson400() throws Exception {
        for(String path:Arrays.asList("/user/merchants/0","/user/merchants/-1","/user/merchants/abc","/user/merchants/9223372036854775808","/user/stores/0/context","/user/merchants?page=0","/user/merchants?pageSize=51","/user/merchants?pageSize=0","/user/merchants?page=abc"))
            mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/user/merchants").param("keyword",String.join("",Collections.nCopies(101,"a"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0));
    }
    @Test void redisOutageReturns503InsteadOfInventingOpenStatus() throws Exception {
        when(redis.opsForValue().get(anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("private host"));
        mvc.perform(get("/user/stores/11/context")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(0));
    }
}
