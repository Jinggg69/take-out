package com.junzhou.test;

import com.junzhou.config.WebMvcConfiguration;
import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.controller.user.ShoppingCartController;
import com.junzhou.dto.ShoppingCartDTO;
import com.junzhou.exception.BaseException;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.interceptor.JwtTokenAdminInterceptor;
import com.junzhou.interceptor.JwtTokenUserInterceptor;
import com.junzhou.service.AdminSessionService;
import com.junzhou.service.CartAccessService;
import com.junzhou.service.OrderPricingService;
import com.junzhou.service.ShoppingCartService;
import com.junzhou.service.impl.OrderServiceImpl;
import com.junzhou.service.impl.ShoppingCartServiceImpl;
import com.junzhou.utils.JwtUtil;
import com.junzhou.websocket.WebSocketServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP boundaries, MyBatis statements and transactions; no cart mocks. */
class ShoppingCartModuleTest {
    private AnnotationConfigWebApplicationContext context;
    private JdbcTemplate jdbc;
    private MockMvc mvc;
    private ShoppingCartService carts;

    @BeforeEach void setUp() {
        BaseContext.removeCurrentId();
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(OrderPaymentModuleTest.PaymentTestConfiguration.class, WebMvcConfiguration.class,
                ShoppingCartController.class, ShoppingCartServiceImpl.class, OrderServiceImpl.class,
                com.junzhou.service.impl.OrderPricingServiceImpl.class, GlobalExceptionHandler.class, JwtTokenUserInterceptor.class,
                JwtTokenAdminInterceptor.class, com.junzhou.service.impl.AdminSessionServiceImpl.class, WebSocketServer.class, com.junzhou.service.impl.CartAccessServiceImpl.class);
        context.refresh();
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.execute("create table `user` (id bigint primary key, openid varchar(64))");
        jdbc.execute("insert into `user` values (42,'cart-42'),(84,'cart-84')");
        jdbc.execute("create table shopping_cart (id bigint auto_increment primary key, user_id bigint not null, "
                + "name varchar(32), dish_id bigint, setmeal_id bigint, dish_flavor varchar(50), "
                + "number int not null, amount decimal(10,2) not null, image varchar(255), create_time timestamp)");
        jdbc.execute("create table dish (id bigint primary key, name varchar(32), price decimal(10,2), image varchar(255), status int)");
        jdbc.execute("insert into dish values (5,'当前菜品',12.50,'dish.png',1)");
        jdbc.execute("create table setmeal (id bigint primary key, name varchar(32), price decimal(10,2), image varchar(255), status int)");
        jdbc.execute("insert into setmeal values (5,'同号套餐',30.00,'setmeal.png',1)");
        jdbc.execute("create table setmeal_dish (id bigint auto_increment primary key, setmeal_id bigint, dish_id bigint, name varchar(32), price decimal(10,2), copies int)");
        jdbc.execute("insert into setmeal_dish (setmeal_id,dish_id,copies) values (5,5,2)");
        seed(84, "微辣", 7);
        carts = context.getBean(ShoppingCartService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach void tearDown() {
        BaseContext.removeCurrentId();
        if (jdbc != null) jdbc.execute("SET DB_CLOSE_DELAY 0");
        if (context != null) context.close();
    }

    @Test void flavorsIdentifyDifferentItemsAndExactAddsMerge() throws Exception {
        action("add", "{\"dishId\":5,\"dishFlavor\":\"微辣,少盐\",\"userId\":84}", 1);
        action("add", "{\"dishId\":5,\"dishFlavor\":\"重辣\"}", 1);
        action("add", "{\"dishId\":5,\"dishFlavor\":\"微辣,少盐\"}", 1);
        assertEquals(2, count());
        assertEquals(2, quantity("微辣,少盐"));
        assertEquals(1, quantity("重辣"));
        assertEquals(7, jdbc.queryForObject("select number from shopping_cart where user_id=84", Integer.class));
    }

    @Test void missingEmptyAndWhitespaceFlavorAreOneItem() throws Exception {
        for (String flavor : new String[]{"null", "\"\"", "\"   \"", "\"\\t\""}) {
            action("add", "{\"dishId\":5,\"dishFlavor\":" + flavor + "}", 1);
        }
        assertEquals(1, count());
        assertEquals(4, quantity(""));
    }

    @Test void sameNumericDishAndSetmealIdsStaySeparate() throws Exception {
        action("add", "{\"dishId\":5}", 1);
        action("add", "{\"setmealId\":5}", 1);
        assertEquals(2, count());
        action("sub", "{\"setmealId\":5}", 1);
        assertEquals(1, count());
        assertEquals(5L, jdbc.queryForObject("select dish_id from shopping_cart where user_id=42", Long.class));
    }

    @Test void subtractOnlyTheSpecifiedFlavorAndDeleteAtZero() throws Exception {
        seed(42, "微辣", 2);
        seed(42, "重辣", 1);
        action("sub", "{\"dishId\":5,\"dishFlavor\":\"重辣\"}", 1);
        assertEquals(1, count());
        assertEquals(2, quantity("微辣"));
        action("sub", "{\"dishId\":5,\"dishFlavor\":\"不存在\"}", 1);
        assertEquals(2, quantity("微辣"));
        action("sub", "{\"dishId\":5,\"dishFlavor\":\"微辣\"}", 1);
        assertEquals(1, quantity("微辣"));
        action("sub", "{\"dishId\":5,\"dishFlavor\":\"微辣\"}", 1);
        assertEquals(0, count());
        assertEquals(7, jdbc.queryForObject("select number from shopping_cart where user_id=84", Integer.class));
    }

    @ParameterizedTest @ValueSource(strings={"{}", "{\"dishId\":5,\"setmealId\":5}", "{\"dishId\":0}", "{\"setmealId\":-1}"})
    void malformedItemKeysAreBusinessRejections(String body) {
        for (String operation : new String[]{"add","sub"}) {
            assertDoesNotThrow(() -> action(operation, body, 0));
        }
        assertEquals(0, count());
    }

    @Test void overlongFlavorIsRejectedBeforeAnyWrite() {
        String body = "{\"dishId\":5,\"dishFlavor\":\"" + String.join("", Collections.nCopies(51,"辣")) + "\"}";
        assertDoesNotThrow(() -> action("add", body, 0));
        assertEquals(0, count());
    }

    @Test void missingIdentityCannotReadOrMutateAnyCart() {
        assertThrows(BaseException.class, () -> carts.showShoppingCart());
        assertThrows(BaseException.class, () -> carts.clean());
        ShoppingCartDTO input = new ShoppingCartDTO(); input.setDishId(5L);
        assertThrows(BaseException.class, () -> carts.add(input));
        assertThrows(BaseException.class, () -> carts.sub(input));
        assertEquals(7, jdbc.queryForObject("select number from shopping_cart where user_id=84", Integer.class));
    }

    @Test void deletedUserCannotReadOrWriteWithAnOldSignedIdentity() throws Exception {
        jdbc.update("delete from `user` where id=42");
        action("add", "{\"dishId\":5}", 0);
        mvc.perform(get("/user/shoppingCart/list").header("authentication", token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
        assertEquals(0, count());
    }

    @ParameterizedTest @ValueSource(strings={"update dish set status=0 where id=5", "delete from dish where id=5"})
    void unavailableItemsCannotIncreaseButCanBeRemoved(String mutation) throws Exception {
        seed(42,"微辣",1);
        jdbc.execute(mutation);
        action("add", "{\"dishId\":5,\"dishFlavor\":\"微辣\"}", 0);
        assertEquals(1, quantity("微辣"));
        action("sub", "{\"dishId\":5,\"dishFlavor\":\"微辣\"}", 1);
        assertEquals(0, count());
    }

    @Test void legacyDuplicateRowsAndWhitespaceAreConsolidatedWithoutLosingUnits() throws Exception {
        seed(42,null,2); seed(42,"  ",3); seed(42,"重辣",4);
        action("add", "{\"dishId\":5}", 1);
        assertEquals(2, count());
        assertEquals(6, quantity(""));
        assertEquals(4, quantity("重辣"));
        seed(42," 微辣 ",2); seed(42,"微辣",3);
        action("sub", "{\"dishId\":5,\"dishFlavor\":\"微辣\"}",1);
        assertEquals(4, quantity("微辣"));
        assertEquals(3, count());
    }

    @Test void overflowRejectsAndKeepsExistingQuantity() throws Exception {
        seed(42,"微辣",Integer.MAX_VALUE);
        action("add", "{\"dishId\":5,\"dishFlavor\":\"微辣\"}",0);
        assertEquals(Integer.MAX_VALUE, quantity("微辣"));
    }

    @Test void listAndCleanAreScopedToCurrentUser() throws Exception {
        seed(42,"微辣",2);
        mvc.perform(get("/user/shoppingCart/list").header("authentication", token()).param("userId","84"))
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].number").value(2));
        mvc.perform(delete("/user/shoppingCart/clean").header("authentication",token()))
                .andExpect(jsonPath("$.code").value(1));
        assertEquals(0,count());
        assertEquals(7,jdbc.queryForObject("select number from shopping_cart where user_id=84",Integer.class));
        mvc.perform(post("/user/shoppingCart/add").contentType("application/json").content("{\"dishId\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(ints={0,1})
    void concurrentAddsWaitForTheUserTransactionAndNeverDuplicateOrLoseUnits(int initial) throws Exception {
        if (initial>0) seed(42,"微辣",initial);
        runConcurrentUnderUserLock(6, () -> { ShoppingCartDTO input = new ShoppingCartDTO(); input.setDishId(5L); input.setDishFlavor("微辣"); carts.add(input); });
        assertEquals(1,count());
        assertEquals(initial+6,quantity("微辣"));
    }

    @Test void concurrentSubtractsCannotGoNegativeOrDeleteAnotherFlavor() throws Exception {
        seed(42,"微辣",3); seed(42,"重辣",4);
        runConcurrentUnderUserLock(6, () -> { ShoppingCartDTO input = new ShoppingCartDTO(); input.setDishId(5L); input.setDishFlavor("微辣"); carts.sub(input); });
        assertEquals(1,count());
        assertEquals(4,quantity("重辣"));
    }

    @Test void cleanWaitsForTheSameUserTransaction() throws Exception {
        seed(42,"微辣",2);
        runConcurrentUnderUserLock(1, () -> carts.clean());
        assertEquals(0,count());
        assertEquals(7,jdbc.queryForObject("select number from shopping_cart where user_id=84",Integer.class));
    }

    private void runConcurrentUnderUserLock(int threads, Runnable operation) throws Exception {
        jdbc.execute("set default_lock_timeout 10000");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        try {
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                jdbc.queryForObject("select id from `user` where id=42 for update",Long.class);
                for(int i=0;i<threads;i++) futures.add(pool.submit(() -> {
                    BaseContext.setCurrentId(42L);
                    try { operation.run(); } finally { BaseContext.removeCurrentId(); }
                }));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                boolean blocked=false;
                while(System.nanoTime()<deadline) {
                    for (Future<?> future : futures) {
                        if (future.isDone()) {
                            try { future.get(); }
                            catch (Exception ex) { throw new AssertionError("Cart worker failed before the lock was released", ex); }
                            fail("Cart worker completed before the user lock was released");
                        }
                    }
                    if(jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",Integer.class)>=threads) { blocked=true; break; }
                    Thread.yield();
                }
                assertTrue(blocked,"Every cart mutation must wait for the held user transaction");
                return null;
            });
            for(Future<?> future:futures) future.get(15,TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow(); assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS));
        }
    }

    private void seed(long user,String flavor,int number) {
        jdbc.update("insert into shopping_cart (user_id,dish_id,dish_flavor,number,amount,name) values (?,5,?,?,12.50,'旧名称')",user,flavor,number);
    }
    private int count() { return jdbc.queryForObject("select count(*) from shopping_cart where user_id=42",Integer.class); }
    private int quantity(String flavor) { return jdbc.queryForObject("select coalesce(sum(number),0) from shopping_cart where user_id=42 and dish_flavor=?",Integer.class,flavor); }
    private ResultActions action(String operation,String body,int code) throws Exception {
        return mvc.perform(post("/user/shoppingCart/"+operation).header("authentication",token()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(code));
    }
    private String token() {
        Map<String,Object> claims=new HashMap<>(); claims.put(JwtClaimsConstant.USER_ID,42L);
        return JwtUtil.createJWT("order-payment-test-secret",60000L,claims);
    }
}
