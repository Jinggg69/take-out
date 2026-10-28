package com.junzhou.test;

import com.junzhou.mapper.OrderMapper;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.task.OrderTask;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real MyBatis/H2 tests; no application server, Redis, or payment provider is started. */
class OrderMaintenanceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    private JdbcTemplate jdbc;
    private OrderTask task;
    private OrderMapper orders;
    private OrderMaintenanceMapper maintenance;
    private TransactionTemplate transactions;
    private ExecutorService workers;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table orders (id bigint primary key, number varchar(50), status int, pay_status int, "
                + "order_time timestamp, dispatch_time timestamp, delivery_time timestamp, checkout_time timestamp, "
                + "cancel_time timestamp, cancel_reason varchar(255), remark varchar(255), amount decimal(10,2))");
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        configuration.addMapper(OrderMapper.class);
        configuration.addMapper(OrderMaintenanceMapper.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/OrderMapper.xml"));
        SqlSessionFactory sessions = factory.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sessions);
        orders = template.getMapper(OrderMapper.class);
        maintenance = template.getMapper(OrderMaintenanceMapper.class);
        task = new OrderTask(maintenance, CLOCK);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        workers = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws Exception {
        workers.shutdownNow();
        assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void timeoutCancelsOnlyUnpaidOrdersStrictlyOlderThanFifteenMinutes() {
        add(1, 1, 0, NOW.minusMinutes(15).minusSeconds(1), null);
        add(2, 1, 0, NOW.minusMinutes(15), null);
        add(3, 1, 0, NOW.minusMinutes(15).plusSeconds(1), null);
        task.processTimeoutOrders();
        assertEquals(6, status(1));
        assertEquals(0, payment(1));
        assertEquals(NOW, time(1, "cancel_time"));
        assertFalse(jdbc.queryForObject("select cancel_reason from orders where id=1", String.class).isEmpty());
        assertEquals(1, status(2));
        assertNull(time(2, "cancel_time"));
        assertEquals(1, status(3));
        assertNull(time(3, "cancel_time"));
        assertEquals("keep", jdbc.queryForObject("select remark from orders where id=1", String.class));
    }

    @Test
    void timeoutPreservesPaidCancelledAndRefundPendingOrders() {
        add(1, 1, 1, NOW.minusHours(2), null);
        add(2, 2, 1, NOW.minusHours(2), null);
        add(3, 6, 0, NOW.minusHours(2), null);
        add(4, 6, 3, NOW.minusHours(2), null);
        add(5, 1, 2, NOW.minusHours(2), null);
        add(6, 0, 0, NOW.minusHours(2), null);
        Map<String, Object>[] before = snapshots(6);
        task.processTimeoutOrders();
        assertSnapshotsUnchanged(before);
    }

    @Test
    void deliveryUsesDispatchTimeAndStrictOneHourBoundary() {
        add(1, 4, 1, NOW.minusHours(5), NOW.minusHours(1).minusSeconds(1));
        add(2, 4, 1, NOW.minusHours(5), NOW.minusHours(1));
        add(3, 4, 1, NOW.minusHours(5), NOW.minusHours(1).plusSeconds(1));
        add(4, 4, 1, NOW.minusHours(5), NOW.minusMinutes(10));
        task.processDeliveryOrders();
        assertEquals(5, status(1));
        assertEquals(NOW, time(1, "delivery_time"));
        assertEquals(NOW.minusHours(1).minusSeconds(1), time(1, "dispatch_time"));
        for (long id = 2; id <= 4; id++) {
            assertEquals(4, status(id));
            assertNull(time(id, "delivery_time"));
        }
    }

    @Test
    void deliverySkipsLegacyMissingDispatchAndCannotReviveCancelledOrUnpaidOrders() {
        add(1, 4, 1, NOW.minusHours(5), null);
        add(2, 6, 1, NOW.minusHours(5), NOW.minusHours(2));
        add(3, 4, 0, NOW.minusHours(5), NOW.minusHours(2));
        add(4, 4, 2, NOW.minusHours(5), NOW.minusHours(2));
        add(5, 4, 3, NOW.minusHours(5), NOW.minusHours(2));
        add(6, 5, 1, NOW.minusHours(5), NOW.minusHours(2));
        Map<String, Object>[] before = snapshots(6);
        task.processDeliveryOrders();
        assertSnapshotsUnchanged(before);
    }

    @Test
    void repeatedRunsPreserveOriginalCancellationAndCompletionTimes() {
        add(1, 1, 0, NOW.minusHours(2), null);
        add(2, 4, 1, NOW.minusHours(3), NOW.minusHours(2));
        task.processTimeoutOrders();
        task.processDeliveryOrders();
        assertEquals(6, status(1));
        assertEquals(5, status(2));
        Map<String, Object>[] before = snapshots(2);
        task = new OrderTask(maintenance, Clock.offset(CLOCK, Duration.ofHours(2)));
        task.processTimeoutOrders();
        task.processDeliveryOrders();
        assertSnapshotsUnchanged(before);
    }

    @Test
    void paymentWinningRowLockPreventsTimeoutFromOverwritingPaidState() throws Exception {
        add(1, 1, 0, NOW.minusHours(2), null);
        CountDownLatch paymentUpdated = new CountDownLatch(1);
        CountDownLatch releasePayment = new CountDownLatch(1);
        Future<?> payment = workers.submit(() -> transactions.execute(status -> {
            assertEquals(1, orders.markPaid("order-1"));
            paymentUpdated.countDown();
            await(releasePayment);
            return null;
        }));
        try {
            assertTrue(paymentUpdated.await(5, TimeUnit.SECONDS));
            Future<?> timeout = workers.submit(task::processTimeoutOrders);
            assertDatabaseRowLockWait(timeout);
            releasePayment.countDown();
            payment.get(10, TimeUnit.SECONDS);
            timeout.get(10, TimeUnit.SECONDS);
            assertEquals(2, status(1));
            assertEquals(1, payment(1));
            assertNotNull(time(1, "checkout_time"));
            assertNull(time(1, "cancel_time"));
        } finally {
            releasePayment.countDown();
        }
    }

    @Test
    void timeoutWinningRowLockPreventsPaymentTransitionFromRevivingCancelledOrder() throws Exception {
        add(1, 1, 0, NOW.minusHours(2), null);
        CountDownLatch timeoutUpdated = new CountDownLatch(1);
        CountDownLatch releaseTimeout = new CountDownLatch(1);
        Future<?> timeout = workers.submit(() -> transactions.execute(status -> {
            task.processTimeoutOrders();
            timeoutUpdated.countDown();
            await(releaseTimeout);
            return null;
        }));
        try {
            assertTrue(timeoutUpdated.await(5, TimeUnit.SECONDS));
            Future<Integer> payment = workers.submit(() -> orders.markPaid("order-1"));
            assertDatabaseRowLockWait(payment);
            releaseTimeout.countDown();
            timeout.get(10, TimeUnit.SECONDS);
            assertEquals(0, payment.get(10, TimeUnit.SECONDS));
            assertEquals(6, status(1));
            assertEquals(0, payment(1));
            assertEquals(NOW, time(1, "cancel_time"));
            assertNull(time(1, "checkout_time"));
        } finally {
            releaseTimeout.countDown();
        }
    }

    private void assertDatabaseRowLockWait(Future<?> operation) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (jdbc.queryForObject("select count(*) from information_schema.sessions where blocker_id is not null",
                    Integer.class) > 0) {
                return;
            }
            if (operation.isDone()) {
                operation.get();
                fail("Expected the concurrent conditional update to wait on the held database row lock");
            }
            Thread.yield();
        }
        fail("Concurrent update never waited on a database row lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release database transaction");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private void add(long id, int status, int payment, LocalDateTime orderTime, LocalDateTime dispatchTime) {
        jdbc.update("insert into orders (id,number,status,pay_status,order_time,dispatch_time,remark,amount) "
                + "values (?,?,?,?,?,?,'keep',25.00)", id, "order-" + id, status, payment, orderTime, dispatchTime);
    }

    private int status(long id) {
        return jdbc.queryForObject("select status from orders where id=?", Integer.class, id);
    }

    private int payment(long id) {
        return jdbc.queryForObject("select pay_status from orders where id=?", Integer.class, id);
    }

    private LocalDateTime time(long id, String column) {
        Timestamp value = jdbc.queryForObject("select " + column + " from orders where id=?", Timestamp.class, id);
        return value == null ? null : value.toLocalDateTime();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object>[] snapshots(int count) {
        Map<String, Object>[] result = new Map[count];
        for (int i = 0; i < count; i++) {
            result[i] = jdbc.queryForMap("select * from orders where id=?", i + 1);
        }
        return result;
    }

    private void assertSnapshotsUnchanged(Map<String, Object>[] before) {
        for (int i = 0; i < before.length; i++) {
            assertEquals(before[i], jdbc.queryForMap("select * from orders where id=?", i + 1), "order " + (i + 1));
        }
    }

}
