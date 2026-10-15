package com.sky.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.config.WebMvcConfiguration;
import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.controller.user.OrderController;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.interceptor.JwtTokenAdminInterceptor;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.mapper.*;
import com.sky.properties.JwtProperties;
import com.sky.properties.WeChatProperties;
import com.sky.service.OrderService;
import com.sky.service.impl.OrderServiceImpl;
import com.sky.utils.JwtUtil;
import com.sky.utils.WeChatPayUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.annotations.Mapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC, service, MyBatis and AES decryption; only the external payment call is replaced. */
class OrderPaymentModuleTest {
    private static final String API_KEY = "0123456789abcdef0123456789abcdef";
    private static final String JWT_SECRET = "order-payment-test-secret";
    private AnnotationConfigWebApplicationContext context;
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        BaseContext.removeCurrentId();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class, OrderController.class, OrderServiceImpl.class,
                GlobalExceptionHandler.class, JwtTokenUserInterceptor.class, JwtTokenAdminInterceptor.class,
                PaymentTestConfiguration.class);
        context.refresh();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("create table orders (id bigint auto_increment primary key, number varchar(50), "
                + "status int not null, user_id bigint not null, address_book_id bigint not null, "
                + "order_time timestamp not null, checkout_time timestamp, pay_method int not null, "
                + "pay_status int not null, amount decimal(10,2) not null, remark varchar(100), "
                + "user_name varchar(32), phone varchar(11), address varchar(255), consignee varchar(32), "
                + "cancel_reason varchar(255), rejection_reason varchar(255), cancel_time timestamp, "
                + "estimated_delivery_time timestamp, delivery_status int, delivery_time timestamp, "
                + "pack_amount int, tableware_number int, tableware_status int)");
        jdbc.execute("create table order_detail (id bigint auto_increment primary key, name varchar(32), "
                + "order_id bigint not null, dish_id bigint, setmeal_id bigint, dish_flavor varchar(50), "
                + "number int not null, amount decimal(10,2) not null, image varchar(255))");
        jdbc.execute("create table address_book (id bigint primary key, user_id bigint, "
                + "phone varchar(11), consignee varchar(32), detail varchar(200))");
        jdbc.execute("create table shopping_cart (id bigint primary key, user_id bigint, name varchar(100), "
                + "dish_id bigint, setmeal_id bigint, dish_flavor varchar(50), number int, "
                + "amount decimal(10,2), image varchar(255))");
        jdbc.execute("create table `user` (id bigint primary key, openid varchar(64))");
        jdbc.update("insert into `user` values (42, 'openid-42')");
        jdbc.update("insert into address_book values (101, 42, '13800000000', '张三', '测试路1号')");
        jdbc.update("insert into shopping_cart (id, user_id, name, dish_id, number, amount) values "
                + "(1, 42, '测试菜品', 5, 2, 12.50), (2, 84, '其他用户菜品', 6, 1, 20.00)");
        jdbc.update("insert into orders (id, number, status, user_id, address_book_id, order_time, "
                + "pay_method, pay_status, amount, remark) values "
                + "(10, 'order-10', 1, 42, 101, current_timestamp, 1, 0, 25.00, '保留备注')");
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
        if (jdbc != null) {
            jdbc.execute("SET DB_CLOSE_DELAY 0");
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void applicationScanningRegistersOrderDetailMapper() {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
        scanner.setAnnotationClass(Mapper.class);
        scanner.registerFilters();
        scanner.scan("com.sky.mapper");
        assertTrue(registry.containsBeanDefinition("orderDetailMapper"),
                "OrderService must be able to inject OrderDetailMapper during application startup");
    }

    @Test
    void signedSubmissionPersistsDetailsAndClearsOnlyCurrentUsersCart() throws Exception {
        mvc.perform(post("/user/order/submit").header("authentication", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressBookId\":101,"
                                + "\"payMethod\":1,\"amount\":25.00,\"packAmount\":0,\"tablewareNumber\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.orderAmount").value(25.00));
        assertEquals(2, jdbc.queryForObject("select count(*) from orders", Integer.class));
        assertEquals("测试路1号", jdbc.queryForObject("select address from orders where id <> 10", String.class));
        assertEquals(42L, jdbc.queryForObject("select user_id from orders where id <> 10", Long.class));
        assertEquals(2, jdbc.queryForObject("select number from order_detail", Integer.class));
        assertEquals(5L, jdbc.queryForObject("select dish_id from order_detail", Long.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from shopping_cart where user_id = 42", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from shopping_cart where user_id = 84", Integer.class));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void detailInsertFailureRollsBackOrderAndPreservesCart() {
        jdbc.update("update shopping_cart set name = ? where id = 1", String.join("", Collections.nCopies(33, "x")));
        OrdersSubmitDTO submit = new OrdersSubmitDTO();
        submit.setAddressBookId(101L);
        submit.setPayMethod(1);
        submit.setAmount(new BigDecimal("25.00"));
        submit.setPackAmount(0);
        submit.setTablewareNumber(2);
        BaseContext.setCurrentId(42L);
        assertThrows(RuntimeException.class, () -> context.getBean(OrderService.class).orderSubmit(submit));
        assertEquals(1, jdbc.queryForObject("select count(*) from orders", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_detail", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from shopping_cart where user_id = 42", Integer.class));
    }

    @Test
    void prepaymentReturnsClientParametersWithoutMarkingOrderPaid() throws Exception {
        mvc.perform(put("/user/order/payment").header("authentication", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNumber\":\"order-10\",\"payMethod\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.packageStr").value("prepay_id=test-prepay"))
                .andExpect(jsonPath("$.data.nonceStr").value("test-nonce"))
                .andExpect(jsonPath("$.data.timeStamp").value("1700000000"))
                .andExpect(jsonPath("$.data.signType").value("RSA"))
                .andExpect(jsonPath("$.data.paySign").value("test-signature"));
        assertEquals(0, jdbc.queryForObject("select pay_status from orders where id = 10", Integer.class));
        assertNull(BaseContext.getCurrentId());
    }

    @Test
    void alreadyPaidResponseBecomesBusinessError() throws Exception {
        context.getBean(TestWeChatPayUtil.class).alreadyPaid = true;
        mvc.perform(put("/user/order/payment").header("authentication", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderNumber\":\"order-10\",\"payMethod\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("该订单已支付"));
    }

    @Test
    void anonymousPaymentIsRejected() throws Exception {
        mvc.perform(put("/user/order/payment").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNumber\":\"order-10\",\"payMethod\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void encryptedCallbackUpdatesPaymentAndAcknowledgesWithoutUserToken() throws Exception {
        mvc.perform(post("/notify/paySuccess").contentType(MediaType.APPLICATION_JSON)
                        .content(encryptedNotification()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        assertEquals(1, jdbc.queryForObject("select pay_status from orders where id = 10", Integer.class));
        assertEquals(2, jdbc.queryForObject("select status from orders where id = 10", Integer.class));
        assertNotNull(jdbc.queryForObject("select checkout_time from orders where id = 10", java.sql.Timestamp.class));
        assertEquals("保留备注", jdbc.queryForObject("select remark from orders where id = 10", String.class));
        assertEquals(0, new BigDecimal("25.00").compareTo(jdbc.queryForObject(
                "select amount from orders where id = 10", BigDecimal.class)));
    }

    private String token() {
        return JwtUtil.createJWT(JWT_SECRET, 60000,
                new HashMap<>(Collections.singletonMap(JwtClaimsConstant.USER_ID, 42L)));
    }

    private String encryptedNotification() throws Exception {
        String nonce = "0123456789ab";
        String associatedData = "transaction";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(API_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        String plainText = "{\"out_trade_no\":\"order-10\",\"transaction_id\":\"wx-10\",\"trade_state\":\"SUCCESS\"}";
        JSONObject resource = new JSONObject();
        resource.put("nonce", nonce);
        resource.put("associated_data", associatedData);
        resource.put("ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8))));
        JSONObject body = new JSONObject();
        body.put("resource", resource);
        return body.toJSONString();
    }

    static class TestWeChatPayUtil extends WeChatPayUtil {
        boolean alreadyPaid;

        @Override
        public JSONObject pay(String number, BigDecimal total, String description, String openid) {
            if (!"order-10".equals(number) || !"openid-42".equals(openid)) {
                throw new IllegalArgumentException("Incorrect order or payer passed to WeChat");
            }
            if (alreadyPaid) {
                return JSON.parseObject("{\"code\":\"ORDERPAID\",\"message\":\"订单已支付\"}");
            }
            return JSON.parseObject("{\"nonceStr\":\"test-nonce\",\"timeStamp\":\"1700000000\","
                    + "\"signType\":\"RSA\",\"paySign\":\"test-signature\",\"package\":\"prepay_id=test-prepay\"}");
        }
    }

    @TestConfiguration
    @EnableTransactionManagement
    @ComponentScan("com.sky.controller.notify")
    static class PaymentTestConfiguration {
        @Bean
        DataSource dataSource() {
            JdbcDataSource source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
            return source;
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.setCacheEnabled(false);
            config.addMapper(UserMapper.class);
            config.addMapper(AddressBookMapper.class);
            factory.setConfiguration(config);
            factory.setMapperLocations(new ClassPathResource("mapper/OrderMapper.xml"),
                    new ClassPathResource("mapper/OrderDetailMapper.xml"), new ClassPathResource("mapper/ShoppingCartMapper.xml"));
            return factory.getObject();
        }

        @Bean OrderMapper orderMapper(SqlSessionFactory f) { return new SqlSessionTemplate(f).getMapper(OrderMapper.class); }
        @Bean OrderDetailMapper orderDetailMapper(SqlSessionFactory f) { return new SqlSessionTemplate(f).getMapper(OrderDetailMapper.class); }
        @Bean ShoppingCartMapper shoppingCartMapper(SqlSessionFactory f) { return new SqlSessionTemplate(f).getMapper(ShoppingCartMapper.class); }
        @Bean AddressBookMapper addressBookMapper(SqlSessionFactory f) { return new SqlSessionTemplate(f).getMapper(AddressBookMapper.class); }
        @Bean UserMapper userMapper(SqlSessionFactory f) { return new SqlSessionTemplate(f).getMapper(UserMapper.class); }
        @Bean TestWeChatPayUtil weChatPayUtil() { return new TestWeChatPayUtil(); }

        @Bean
        WeChatProperties weChatProperties() {
            WeChatProperties properties = new WeChatProperties();
            properties.setApiV3Key(API_KEY);
            return properties;
        }

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties properties = new JwtProperties();
            properties.setUserSecretKey(JWT_SECRET);
            properties.setUserTokenName("authentication");
            return properties;
        }
    }
}
