package com.sky.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageInterceptor;
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
import com.sky.service.DeliveryRangeService;
import com.sky.properties.ShopProperties;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.web.client.RestTemplate;
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
    private int deliveryDistance = 1000;
    private MockRestServiceServer mapServer;

    @BeforeEach
    void setUp() {
        BaseContext.removeCurrentId();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class, OrderController.class, OrderServiceImpl.class,
                GlobalExceptionHandler.class, JwtTokenUserInterceptor.class, JwtTokenAdminInterceptor.class,
                PaymentTestConfiguration.class);
        context.refresh();
        mapServer = MockRestServiceServer.bindTo(context.getBean(RestTemplate.class)).build();
        mapServer.expect(ExpectedCount.between(0, Integer.MAX_VALUE),
                org.springframework.test.web.client.match.MockRestRequestMatchers.anything()).andRespond(request -> {
            String response = request.getURI().getPath().contains("geocoding")
                    ? "{\"status\":0,\"result\":{\"location\":{\"lat\":40,\"lng\":116}}}"
                    : "{\"status\":0,\"result\":{\"routes\":[{\"distance\":" + deliveryDistance + "}]}}";
            return org.springframework.test.web.client.response.MockRestResponseCreators
                    .withSuccess(response, MediaType.APPLICATION_JSON).createResponse(request);
        });
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
        jdbc.execute("alter table shopping_cart modify id bigint auto_increment");
        jdbc.execute("alter table shopping_cart add create_time timestamp");
        jdbc.execute("create table `user` (id bigint primary key, openid varchar(64))");
        jdbc.update("insert into `user` values (42, 'openid-42')");
        jdbc.update("insert into address_book values (101, 42, '13800000000', '张三', '测试路1号')");
        jdbc.execute("alter table address_book add (province_name varchar(32), city_name varchar(32), district_name varchar(32))");
        jdbc.update("insert into shopping_cart (id, user_id, name, dish_id, number, amount) values "
                + "(1, 42, '测试菜品', 5, 2, 12.50), (2, 84, '其他用户菜品', 6, 1, 20.00)");
        jdbc.update("insert into orders (id, number, status, user_id, address_book_id, order_time, "
                + "pay_method, pay_status, amount, remark) values "
                + "(10, 'order-10', 1, 42, 101, '2026-10-14 12:00:00', 1, 0, 25.00, '保留备注')");
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        if (mapServer != null) {
            mapServer.verify();
        }
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
    void historyIsPaginatedAndCannotBeScopedToAnotherUser() throws Exception {
        addOrder(11, 42, 5, 1);
        addOrder(12, 84, 5, 1);
        jdbc.update("insert into order_detail (name, order_id, dish_id, number, amount) values ('鱼香肉丝', 11, 5, 2, 12.50)");
        mvc.perform(get("/user/order/historyOrders").header("authentication", token())
                        .param("page", "1").param("pageSize", "1").param("userId", "84"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(11))
                .andExpect(jsonPath("$.data.records[0].orderDetailList[0].number").value(2));
        mvc.perform(get("/user/order/historyOrders").header("authentication", token())
                        .param("page", "1").param("pageSize", "10").param("status", "1"))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void userDetailsRejectForeignAndMissingOrders() throws Exception {
        addOrder(11, 84, 2, 1);
        mvc.perform(get("/user/order/orderDetail/10").header("authentication", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.number").value("order-10"));
        for (long id : new long[]{11, 999}) {
            mvc.perform(get("/user/order/orderDetail/" + id).header("authentication", token()))
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Test
    void unpaidCancellationKeepsPaymentUnpaidAndRecordsReasonAndTime() throws Exception {
        mvc.perform(put("/user/order/cancel/10").header("authentication", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(6, statusOf(10));
        assertEquals(0, jdbc.queryForObject("select pay_status from orders where id=10", Integer.class));
        assertNotNull(jdbc.queryForObject("select cancel_time from orders where id=10", java.sql.Timestamp.class));
        assertFalse(jdbc.queryForObject("select cancel_reason from orders where id=10", String.class).isEmpty());
    }

    @Test
    void userCannotCancelAcceptedOrForeignOrders() throws Exception {
        addOrder(11, 42, 3, 1);
        addOrder(12, 84, 1, 0);
        for (long id : new long[]{11, 12, 999}) {
            mvc.perform(put("/user/order/cancel/" + id).header("authentication", token()))
                    .andExpect(jsonPath("$.code").value(0));
        }
        assertEquals(3, statusOf(11));
        assertEquals(1, statusOf(12));
    }

    @Test
    void repetitionAddsDishAndSetmealWithFreshCartIdsAndPreservesExistingCart() throws Exception {
        jdbc.update("insert into order_detail (id, name, order_id, dish_id, dish_flavor, number, amount) "
                + "values (100, '历史菜品', 10, 5, '微辣', 3, 12.50)");
        jdbc.update("insert into order_detail (id, name, order_id, setmeal_id, number, amount) "
                + "values (101, '历史套餐', 10, 7, 1, 30.00)");
        mvc.perform(post("/user/order/repetition/10").header("authentication", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        assertEquals(4, jdbc.queryForObject("select count(*) from shopping_cart", Integer.class));
        assertEquals(3, jdbc.queryForObject("select number from shopping_cart where dish_flavor='微辣'", Integer.class));
        assertEquals(42L, jdbc.queryForObject("select user_id from shopping_cart where setmeal_id=7", Long.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from shopping_cart where id in (100,101)", Integer.class));
        assertEquals(1, jdbc.queryForObject("select count(*) from orders", Integer.class));
    }

    @Test
    void repetitionRejectsForeignOrdersWithoutChangingCart() throws Exception {
        addOrder(11, 84, 5, 1);
        mvc.perform(post("/user/order/repetition/11").header("authentication", token()))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(2, jdbc.queryForObject("select count(*) from shopping_cart", Integer.class));
    }

    @Test
    void adminSearchCombinesFiltersAndIncludesDishes() throws Exception {
        addOrder(11, 84, 2, 1);
        jdbc.update("update orders set phone='13800000000', order_time='2026-10-15 12:00:00' where id=11");
        jdbc.update("insert into order_detail (name, order_id, dish_id, number, amount) values ('鱼香肉丝', 11, 5, 2, 12.50)");
        mvc.perform(get("/admin/order/conditionSearch").header("token", adminToken())
                        .param("page", "1").param("pageSize", "10").param("number", "order-11")
                        .param("phone", "138").param("status", "2")
                        .param("beginTime", "2026-10-15 00:00:00").param("endTime", "2026-10-15 23:59:59"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderDishes").value("鱼香肉丝*2"));
        mvc.perform(get("/admin/order/details/11").header("token", adminToken()))
                .andExpect(jsonPath("$.data.userId").value(84))
                .andExpect(jsonPath("$.data.orderDetailList[0].dishId").value(5));
    }

    @Test
    void statisticsCountsOnlyTheThreeActiveStates() throws Exception {
        addOrder(11, 42, 2, 1);
        addOrder(12, 84, 2, 1);
        addOrder(13, 42, 3, 1);
        addOrder(14, 42, 4, 1);
        addOrder(15, 42, 6, 0);
        mvc.perform(get("/admin/order/statistics").header("token", adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.toBeConfirmed").value(2))
                .andExpect(jsonPath("$.data.confirmed").value(1))
                .andExpect(jsonPath("$.data.deliveryInProgress").value(1));
    }

    @Test
    void adminCanConfirmDeliverAndCompleteOnlyInSequence() throws Exception {
        addOrder(11, 42, 2, 1);
        adminPut("/admin/order/confirm", "{\"id\":11,\"status\":6}", 1);
        assertEquals(3, statusOf(11));
        adminPut("/admin/order/complete/11", "", 0);
        adminPut("/admin/order/delivery/11", "", 1);
        assertEquals(4, statusOf(11));
        adminPut("/admin/order/complete/11", "", 1);
        assertEquals(5, statusOf(11));
        assertNotNull(jdbc.queryForObject("select delivery_time from orders where id=11", java.sql.Timestamp.class));
        adminPut("/admin/order/confirm", "{\"id\":11}", 0);
        adminPut("/admin/order/delivery/11", "", 0);
        adminPut("/admin/order/cancel", "{\"id\":11,\"cancelReason\":\"售罄\"}", 0);
    }

    @Test
    void invalidAdminInputsDoNotMutateOrders() throws Exception {
        adminPut("/admin/order/confirm", "{}", 0);
        adminPut("/admin/order/rejection", "{\"id\":10,\"rejectionReason\":\" \"}", 0);
        adminPut("/admin/order/cancel", "{\"id\":10,\"cancelReason\":\"\"}", 0);
        adminPut("/admin/order/delivery/999", "", 0);
        assertEquals(1, statusOf(10));
    }

    @Test
    void paidUserCancellationRequestsRefundAndCannotBeRepeated() throws Exception {
        addOrder(11, 42, 2, 1);
        mvc.perform(put("/user/order/cancel/11").header("authentication", token()))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(6, statusOf(11));
        assertEquals(2, jdbc.queryForObject("select pay_status from orders where id=11", Integer.class));
        context.getBean(TestWeChatPayUtil.class).refundResult = "{\"status\":\"CLOSED\"}";
        mvc.perform(put("/user/order/cancel/11").header("authentication", token()))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rejectionAndMerchantCancellationPersistTheirOwnReasons() throws Exception {
        addOrder(11, 42, 2, 1);
        addOrder(12, 84, 3, 1);
        adminPut("/admin/order/rejection", "{\"id\":11,\"rejectionReason\":\"菜品售罄\"}", 1);
        adminPut("/admin/order/cancel", "{\"id\":12,\"cancelReason\":\"无法配送\"}", 1);
        assertEquals(6, statusOf(11));
        assertEquals(6, statusOf(12));
        assertEquals("菜品售罄", jdbc.queryForObject("select rejection_reason from orders where id=11", String.class));
        assertEquals("无法配送", jdbc.queryForObject("select cancel_reason from orders where id=12", String.class));
        assertEquals(2, jdbc.queryForObject("select pay_status from orders where id=12", Integer.class));
    }

    @Test
    void refundFailureKeepsOrderPaidAndUncancelled() throws Exception {
        addOrder(11, 42, 2, 1);
        context.getBean(TestWeChatPayUtil.class).refundResult = "{\"code\":\"SYSTEM_ERROR\",\"message\":\"退款失败\"}";
        adminPut("/admin/order/rejection", "{\"id\":11,\"rejectionReason\":\"售罄\"}", 0);
        assertEquals(2, statusOf(11));
        assertEquals(1, jdbc.queryForObject("select pay_status from orders where id=11", Integer.class));
        assertNull(jdbc.queryForObject("select cancel_time from orders where id=11", java.sql.Timestamp.class));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"timeout", "unknown"})
    void uncertainRefundCannotBeFulfilledAndCanBeRetriedWithSameRefundIdentity(String failure) throws Exception {
        addOrder(11, 42, 2, 1);
        context.getBean(TestWeChatPayUtil.class).refundTimesOut = "timeout".equals(failure);
        if ("unknown".equals(failure)) {
            context.getBean(TestWeChatPayUtil.class).refundResult = "{\"status\":\"UNKNOWN\"}";
        }
        adminPut("/admin/order/rejection", "{\"id\":11,\"rejectionReason\":\"售罄\"}", 0);
        assertEquals(6, statusOf(11));
        assertEquals(1, jdbc.queryForObject("select pay_status from orders where id=11", Integer.class));
        java.sql.Timestamp cancelledAt = jdbc.queryForObject("select cancel_time from orders where id=11", java.sql.Timestamp.class);
        adminPut("/admin/order/confirm", "{\"id\":11}", 0);
        context.getBean(TestWeChatPayUtil.class).refundTimesOut = false;
        context.getBean(TestWeChatPayUtil.class).refundResult = "{\"status\":\"PROCESSING\"}";
        adminPut("/admin/order/rejection", "{\"id\":11,\"rejectionReason\":\"售罄\"}", 1);
        assertEquals(2, jdbc.queryForObject("select pay_status from orders where id=11", Integer.class));
        assertEquals(cancelledAt, jdbc.queryForObject("select cancel_time from orders where id=11", java.sql.Timestamp.class));
    }

    @Test
    void delayedPaymentCallbackCannotResurrectCancelledOrCompletedOrders() {
        addOrder(11, 42, 6, 2);
        addOrder(12, 42, 5, 1);
        OrderService service = context.getBean(OrderService.class);
        service.paySuccess("order-11");
        service.paySuccess("order-12");
        assertEquals(6, statusOf(11));
        assertEquals(5, statusOf(12));
    }

    @Test
    void paymentArrivingAfterUnpaidCancellationIsRefundedWithoutReopeningOrder() throws Exception {
        jdbc.update("update orders set status=6, cancel_reason='用户取消', cancel_time='2026-10-14 13:00:00' where id=10");
        mvc.perform(post("/notify/paySuccess").contentType(MediaType.APPLICATION_JSON).content(encryptedNotification()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        assertEquals(6, statusOf(10));
        assertEquals(2, jdbc.queryForObject("select pay_status from orders where id=10", Integer.class));
        assertEquals("用户取消", jdbc.queryForObject("select cancel_reason from orders where id=10", String.class));
        assertEquals(java.sql.Timestamp.valueOf("2026-10-14 13:00:00"),
                jdbc.queryForObject("select cancel_time from orders where id=10", java.sql.Timestamp.class));
        assertNotNull(jdbc.queryForObject("select checkout_time from orders where id=10", java.sql.Timestamp.class));
    }

    @Test
    void failedLatePaymentRefundReturnsHttp500SoWechatCanRetry() throws Exception {
        jdbc.update("update orders set status=6 where id=10");
        context.getBean(TestWeChatPayUtil.class).refundResult = "{\"code\":\"SYSTEM_ERROR\"}";
        mvc.perform(post("/notify/paySuccess").contentType(MediaType.APPLICATION_JSON).content(encryptedNotification()))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("FAIL"));
        assertEquals(6, statusOf(10));
        assertEquals(0, jdbc.queryForObject("select pay_status from orders where id=10", Integer.class));
        context.getBean(TestWeChatPayUtil.class).refundResult = "{\"status\":\"SUCCESS\"}";
        mvc.perform(post("/notify/paySuccess").contentType(MediaType.APPLICATION_JSON).content(encryptedNotification()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("SUCCESS"));
        assertEquals(2, jdbc.queryForObject("select pay_status from orders where id=10", Integer.class));
    }

    @Test
    void newRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/user/order/historyOrders").param("page", "1").param("pageSize", "10"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/order/statistics")).andExpect(status().isUnauthorized());
    }

    @Test
    void submissionRejectsAnotherUsersAddressBeforeWritingAnything() throws Exception {
        jdbc.update("update address_book set user_id=84 where id=101");
        mvc.perform(post("/user/order/submit").header("authentication", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressBookId\":101,\"amount\":25,\"payMethod\":1,\"packAmount\":0,\"tablewareNumber\":2}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, jdbc.queryForObject("select count(*) from orders", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from shopping_cart", Integer.class));
    }

    @Test
    void outsideDeliveryRangeLeavesOrdersAndCartUntouched() throws Exception {
        deliveryDistance = 5001;
        mvc.perform(post("/user/order/submit").header("authentication", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressBookId\":101,\"amount\":25,\"payMethod\":1,\"packAmount\":0,\"tablewareNumber\":2}"))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, jdbc.queryForObject("select count(*) from orders", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from order_detail", Integer.class));
        assertEquals(2, jdbc.queryForObject("select count(*) from shopping_cart", Integer.class));
    }

    @Test
    void deliveryBoundaryIsAcceptedAndFullAddressIsSaved() throws Exception {
        deliveryDistance = 5000;
        jdbc.update("update address_book set province_name='北京市', city_name='北京市', district_name='海淀区' where id=101");
        mvc.perform(post("/user/order/submit").header("authentication", token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressBookId\":101,\"amount\":25,\"payMethod\":1,\"packAmount\":0,\"tablewareNumber\":2}"))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals("北京市北京市海淀区测试路1号", jdbc.queryForObject("select address from orders where id<>10", String.class));
    }

    private void addOrder(long id, long userId, int state, int payment) {
        jdbc.update("insert into orders (id, number, status, user_id, address_book_id, order_time, "
                + "pay_method, pay_status, amount) values (?, ?, ?, ?, 101, '2026-10-15 12:00:00', 1, ?, 25.00)",
                id, "order-" + id, state, userId, payment);
    }

    private int statusOf(long id) {
        return jdbc.queryForObject("select status from orders where id=?", Integer.class, id);
    }

    private void adminPut(String path, String body, int code) throws Exception {
        mvc.perform(put(path).header("token", adminToken()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }

    private String adminToken() {
        return JwtUtil.createJWT(JWT_SECRET, 60000,
                new HashMap<>(Collections.singletonMap(JwtClaimsConstant.EMP_ID, 1L)));
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
        boolean refundTimesOut;
        String refundResult = "{\"status\":\"PROCESSING\",\"refund_id\":\"wx-refund-test\"}";

        @Override
        public String refund(String number, String refundNumber, BigDecimal refund, BigDecimal total) throws Exception {
            if (!number.equals(refundNumber) || refund.compareTo(new BigDecimal("25.00")) != 0
                    || total.compareTo(new BigDecimal("25.00")) != 0) {
                throw new IllegalArgumentException("Incorrect refund identity or amount");
            }
            if (refundTimesOut) {
                throw new java.net.SocketTimeoutException("Provider may have accepted refund before timeout");
            }
            return refundResult;
        }

        @Override
        public JSONObject pay(String number, BigDecimal total, String description, String openid) {
            if (!"order-10".equals(number) || !"openid-42".equals(openid)
                    || total.compareTo(new BigDecimal("25.00")) != 0) {
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
    @ComponentScan(basePackages = "com.sky.controller", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX,
                    pattern = {"com\\.sky\\.controller\\.notify\\..*", "com\\.sky\\.controller\\.admin\\.OrderController"}))
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
            factory.setPlugins(new PageInterceptor());
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
        @Bean RestTemplate mapRestTemplate() { return new RestTemplate(); }
        @Bean DeliveryRangeService deliveryRangeService(RestTemplate client) {
            ShopProperties properties = new ShopProperties();
            properties.setAddress("北京市海淀区上地十街10号");
            properties.setBaiduAk("test-ak");
            return new DeliveryRangeService(properties, client);
        }

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
            properties.setAdminSecretKey(JWT_SECRET);
            properties.setAdminTokenName("token");
            return properties;
        }
    }
}
