package com.junzhou.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.junzhou.context.RequestScope;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.context.BaseContext;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.interceptor.JwtTokenUserInterceptor;
import com.junzhou.interceptor.RequestScopeInterceptor;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.TenantScopeService;
import com.junzhou.utils.JwtUtil;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SQL and public response contract: removing either ownership predicate leaks seeded orders. */
class MyOrdersIsolationTest {
    JdbcTemplate db; SqlSession session; Object service; Object mapper;
    ObjectMapper json = new ObjectMapper();
    @BeforeEach void setup() throws Exception {
        JdbcDataSource ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);
        db.execute("create table tenant(id bigint primary key,name varchar(80),status int)");
        db.execute("create table store(id bigint primary key,tenant_id bigint,name varchar(80),status int)");
        db.update("insert into tenant values(1,'Current merchant',1),(2,'Other merchant',1),(3,'Disabled',0)");
        db.update("insert into store values(11,1,'Current first',1),(12,1,'Current second',0),(21,2,'Foreign',1),(31,3,'Disabled store',0)");
        db.execute("create table orders(id bigint primary key,number varchar(50),tenant_id bigint,store_id bigint,user_id bigint,order_time timestamp,status int,pay_status int,refund_status varchar(20),amount decimal(10,2),store_snapshot text,pricing_snapshot text,client_request_id varchar(64),fulfillment_type varchar(20),fulfillment_status varchar(32),pack_amount int,contact_name varchar(40),contact_phone varchar(20),consignee varchar(40),phone varchar(20),address varchar(200),remark varchar(200),pickup_number varchar(40),table_snapshot varchar(80),cancel_reason varchar(80),rejection_reason varchar(80),estimated_delivery_time timestamp)");
        db.execute("create table order_detail(id bigint primary key,order_id bigint,name varchar(80),image varchar(200),number int,amount decimal(10,2),dish_flavor varchar(80))");
        order(101,42,1,11,5,1,null); order(102,42,1,12,6,3,"PROCESSING");
        order(103,84,1,11,5,1,null); order(104,42,2,21,5,1,null);order(105,42,3,31,6,4,"ABNORMAL");
        db.update("update orders set store_snapshot=?,pricing_snapshot=?,client_request_id='recovery-102',contact_name='Self',contact_phone='123',pickup_number='P9' where id=102", "{\"merchantName\":\"Historical merchant\",\"storeName\":\"Historical second\"}","{\"goodsAmount\":12.3,\"packFee\":2,\"deliveryFee\":3}");
        Class<?> mc=Class.forName("com.junzhou.mapper.MyOrdersMapper");
        SqlSessionFactoryBean bean=new SqlSessionFactoryBean(); bean.setDataSource(ds);
        org.apache.ibatis.session.SqlSessionFactory f=bean.getObject();
        f.getConfiguration().setMapUnderscoreToCamelCase(true); f.getConfiguration().addMapper(mc);
        session=f.openSession(true); mapper=session.getMapper(mc);
        service=Class.forName("com.junzhou.service.MyOrdersService").getConstructor(mc).newInstance(mapper);
        as(42);
    }
    void order(long id,long user,long merchant,long store,int status,int pay,String refund) {
        db.update("insert into orders(id,number,user_id,tenant_id,store_id,order_time,status,pay_status,refund_status,amount,pack_amount) values(?,?,?,?,?,'2026-11-12 10:20:30',?,?,?,?,?)",id,"N"+id,user,merchant,store,status,pay,refund,17.30,2);
        db.update("insert into order_detail values(?,?,?,'photo',2,6.15,'mild')",id,id,"Dish"+id);
    }
    void as(long id){RequestScope.set(new RequestScope(null,id,null,null,Collections.emptySet()));}
    JsonNode call(String method,Class<?>[] types,Object...args) throws Exception {
        try{return json.valueToTree(service.getClass().getMethod(method,types).invoke(service,args));}
        catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    JsonNode list(Long merchant,Long store,Integer status,Boolean refund,Integer page,Integer size)throws Exception {
        return call("list",new Class[]{Long.class,Long.class,Integer.class,Boolean.class,Integer.class,Integer.class},merchant,store,status,refund,page,size);
    }
    JsonNode detail(long merchant,long order)throws Exception {return call("detail",new Class[]{Long.class,Long.class},merchant,order);}
    JsonNode filters(long merchant)throws Exception{return call("filters",new Class[]{Long.class},merchant);}
    @AfterEach void cleanup(){RequestScope.clear();BaseContext.removeCurrentId();if(session!=null)session.close();if(db!=null)db.execute("SET DB_CLOSE_DELAY 0");}
    @Test void httpUsesRealAuthenticationAndScopeWithoutStoreAndReturnsJsonErrors()throws Exception {
        Object controller=Class.forName("com.junzhou.controller.user.MyOrdersController")
            .getConstructor(service.getClass()).newInstance(service);
        String secret="my-orders-signed-user-test";
        JwtProperties props=new JwtProperties();props.setUserSecretKey(secret);props.setUserTokenName("authentication");
        JwtTokenUserInterceptor auth=new JwtTokenUserInterceptor();ReflectionTestUtils.setField(auth,"jwtProperties",props);
        TenantScopeService scopes=mock(TenantScopeService.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())
            .addInterceptors(auth,new RequestScopeInterceptor(scopes)).build();
        String token=JwtUtil.createJWT(secret,60000,new HashMap<>(Collections.singletonMap("userId",42L)));
        mvc.perform(get("/user/me/orders").param("merchantId","1").param("userId","84")
            .header("authentication",token).header("X-Store-Id","invalid"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get("/user/me/orders/102").param("merchantId","1").header("authentication",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderId").value("102"));
        mvc.perform(get("/user/me/order-filters").param("merchantId","1").header("authentication",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.stores.length()").value(2));
        mvc.perform(get("/user/me/orders").param("merchantId","1"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/user/me/orders").header("authentication",token))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0));
        mvc.perform(get("/user/me/orders/103").param("merchantId","1").header("authentication",token))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(0));
        assertNull(RequestScope.current());assertNull(BaseContext.getCurrentId());verifyNoInteractions(scopes);
    }
    @Test void currentMerchantCrossStoreIsAlwaysBoundToAuthenticatedUser()throws Exception {
        JsonNode result=list(1L,null,null,null,null,null);
        assertEquals(2,result.get("total").asInt());assertEquals("102",result.at("/records/0/orderId").asText());
        assertEquals("101",result.at("/records/1/orderId").asText());
        assertEquals(1,list(1L,11L,null,null,1,10).get("total").asInt());
        assertEquals(1,list(2L,null,null,null,1,10).get("total").asInt());
        as(84); assertEquals(1,list(1L,null,null,null,1,10).get("total").asInt());
        assertEquals(404,assertThrows(MiniappApiException.class,()->detail(1,102)).getStatus());as(42);
        assertEquals(404,assertThrows(MiniappApiException.class,()->detail(2,102)).getStatus());
    }
    @Test void rejectsMissingMerchantCrossMerchantStoreAndInvalidPaging()throws Exception {
        assertEquals(400,assertThrows(MiniappApiException.class,()->list(null,null,null,null,1,10)).getStatus());
        assertEquals(400,assertThrows(MiniappApiException.class,()->list(1L,21L,null,null,1,10)).getStatus());
        assertEquals(400,assertThrows(MiniappApiException.class,()->list(1L,null,null,null,0,10)).getStatus());
        assertEquals(400,assertThrows(MiniappApiException.class,()->list(1L,null,null,null,1,51)).getStatus());
        assertEquals(400,assertThrows(MiniappApiException.class,()->list(1L,null,7,null,1,10)).getStatus());
        RequestScope.clear();assertThrows(Exception.class,()->detail(1,102));
        RequestScope.set(new RequestScope(9L,null,1L,11L,Collections.singleton(11L)));assertThrows(Exception.class,()->filters(1));
    }
    @Test void refundIntersectionSnapshotsAndExplicitWhitelist()throws Exception {
        assertEquals(1,list(1L,null,6,true,1,10).get("total").asInt());
        assertEquals(0,list(1L,null,5,true,1,10).get("total").asInt());
        JsonNode d=detail(1,102);
        assertEquals("Historical merchant",d.get("merchantName").asText());assertEquals("Historical second",d.get("storeName").asText());
        assertFalse(d.get("storeAvailable").asBoolean());assertEquals("17.30",d.get("amount").asText());
        assertEquals("12.30",d.get("goodsAmount").asText());assertEquals("2.00",d.get("packFee").asText());
        assertEquals("3.00",d.get("deliveryFee").asText());assertEquals("6.15",d.at("/orderDetails/0/unitPrice").asText());
        assertEquals("2026-11-12 10:20:30",d.get("orderTime").asText());assertEquals("recovery-102",d.get("clientRequestId").asText());
        assertEquals("12.30",detail(1,101).get("goodsAmount").asText());
        Set<String> expected=new HashSet<>(Arrays.asList("orderId","orderNumber","merchantId","merchantName","storeId","storeName","storeAvailable","orderTime","status","payStatus","refundStatus","fulfillmentType","fulfillmentStatus","amount","totalQuantity","items","clientRequestId","orderDetails","goodsAmount","packFee","deliveryFee","contactName","contactPhone","deliveryAddress","remark","pickupNumber","tableName","cancelReason","rejectionReason","estimatedDeliveryTime"));
        Set<String> actual=new HashSet<>();d.fieldNames().forEachRemaining(actual::add);assertEquals(expected,actual);
        Set<String> itemKeys=new HashSet<>();d.at("/items/0").fieldNames().forEachRemaining(itemKeys::add);assertEquals(new HashSet<>(Arrays.asList("name","image","number")),itemKeys);
    }
    @Test void filtersIncludeOnlyOwnedHistoricalStoresAndDisabledMerchantReadOnly()throws Exception {
        JsonNode f=filters(1);assertEquals("1",f.get("merchantId").asText());assertEquals(2,f.get("stores").size());
        assertEquals("Historical second",f.at("/stores/1/storeName").asText());
        assertEquals(1,list(3L,null,null,true,1,10).get("total").asInt());assertFalse(detail(3,105).get("storeAvailable").asBoolean());
        as(84);assertThrows(Exception.class,()->filters(3));assertThrows(Exception.class,()->list(3L,null,null,null,1,10));
        assertEquals(0,filters(2).get("stores").size());assertThrows(Exception.class,()->filters(999));
    }
    @Test void legacyStoreSnapshotNameAndHistoricalPricesNeverUseCurrentConfiguration()throws Exception {
        db.update("update orders set store_snapshot=? where id=101", "{\"name\":\"Original first\",\"merchantId\":\"999\",\"deliveryFee\":4.5}");
        JsonNode d=detail(1,101);
        assertEquals("Original first",d.get("storeName").asText());assertEquals("1",d.get("merchantId").asText());
        assertEquals("Current merchant",d.get("merchantName").asText());assertEquals("12.30",d.get("goodsAmount").asText());
        assertEquals("4.50",d.get("deliveryFee").asText());assertEquals("2.00",d.get("packFee").asText());
        db.update("update tenant set name='Renamed' where id=1");db.update("update store set name='Renamed store' where id=12");session.clearCache();
        assertEquals("Historical merchant",detail(1,102).get("merchantName").asText());
        assertEquals("Historical second",detail(1,102).get("storeName").asText());
    }
    @Test void detailAndLineItemSqlIndependentlyRejectForeignOwnership()throws Exception {
        Class<?> mc=Class.forName("com.junzhou.mapper.MyOrdersMapper");
        assertNull(mc.getMethod("detail",Long.class,Long.class).invoke(mapper,1L,103L));
        assertNull(mc.getMethod("detail",Long.class,Long.class).invoke(mapper,2L,102L));
        assertTrue(((List<?>)mc.getMethod("details",Long.class,Long.class).invoke(mapper,1L,103L)).isEmpty());
        assertTrue(((List<?>)mc.getMethod("details",Long.class,Long.class).invoke(mapper,2L,102L)).isEmpty());
        assertEquals(1,((List<?>)mc.getMethod("details",Long.class,Long.class).invoke(mapper,1L,102L)).size());
        RequestScope.clear();
        assertThrows(InvocationTargetException.class,()->mc.getMethod("details",Long.class,Long.class).invoke(mapper,1L,102L));
    }
    @Test void nullableHistoricalPackFeePreservesSnapshotOrUnknownInsteadOfInventingZero()throws Exception {
        db.update("update orders set pack_amount=null where id in (101,102)");session.clearCache();
        assertEquals("2.00",detail(1,102).get("packFee").asText());
        assertTrue(detail(1,101).get("packFee").isNull());
        assertTrue(detail(1,101).get("deliveryFee").isNull());
    }
    @Test void nullMalformedOrNonObjectSnapshotsUseOnlyKnownHistoricalValues()throws Exception {
        for(String raw:new String[]{"[]","[1]","true","not-json","{broken",null,"null"}) {
            db.update("update orders set pack_amount=null,store_snapshot=?,pricing_snapshot=? where id=101",raw,raw);session.clearCache();
            JsonNode d=detail(1,101);
            assertEquals("12.30",d.get("goodsAmount").asText(),String.valueOf(raw));
            assertTrue(d.get("packFee").isNull(),String.valueOf(raw));assertTrue(d.get("deliveryFee").isNull(),String.valueOf(raw));
            assertEquals("Current first",d.get("storeName").asText(),String.valueOf(raw));
            assertEquals("Current merchant",d.get("merchantName").asText(),String.valueOf(raw));
        }
    }
    @Test void nonNumericSnapshotAmountsDoNotBecomeServerErrorsOrInventedFees()throws Exception {
        for(String pricing:new String[]{"{\"goodsAmount\":{},\"packFee\":[],\"deliveryFee\":true}","{\"goodsAmount\":\"invalid\",\"packFee\":false,\"deliveryFee\":{}}"}) {
            db.update("update orders set pack_amount=null,store_snapshot=?,pricing_snapshot=? where id=101","{\"deliveryFee\":[]}",pricing);session.clearCache();
            JsonNode d=detail(1,101);assertEquals("12.30",d.get("goodsAmount").asText());
            assertTrue(d.get("packFee").isNull());assertTrue(d.get("deliveryFee").isNull());
        }
    }
}
