package com.junzhou.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.junzhou.context.RequestScope;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.security.AuthorizationSnapshot;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RbacRequestContext;
import com.junzhou.service.PaymentAccountService;
import com.junzhou.service.RbacService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointsBatchServiceTest {
    private static final long EMPLOYEE = 10L;
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate db;
    private TransactionTemplate transactions;
    private PaymentAccountService payments;
    private PointsBatchService batch;

    @BeforeEach
    void createTestData() throws Exception {
        DriverManagerDataSource datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(datasource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        db.execute("create table tenant(id bigint primary key)");
        db.execute("insert into tenant values(1),(2)");
        db.execute("create table store(id bigint primary key, tenant_id bigint, unique(tenant_id,id))");
        db.execute("insert into store values(1,1),(2,1),(3,2)");
        String schema = new String(Files.readAllBytes(Paths.get("../../../database/migrations/20261117_02_membership.sql")),
            StandardCharsets.UTF_8);
        for (String statement : schema.split(";")) {
            if (statement.trim().startsWith("CREATE TABLE")) db.execute(statement);
        }
        db.update("insert into member(id,tenant_id,store_id,member_no,status) values"
            + "(1,1,1,'M1',1),(2,1,2,'M2',1),(3,1,1,'M3',0),(4,2,3,'M4',1)");
        db.update("insert into member_wallet(tenant_id,member_id,environment,points,points_debt)"
            + " values(1,1,'MOCK',0,30),(1,1,'WECHAT',100,0)");
        payments = mock(PaymentAccountService.class);
        when(payments.channelForNewOrders()).thenReturn("MOCK");
        RbacService rbac = mock(RbacService.class);
        doAnswer(invocation -> {
            db.queryForObject("select id from tenant where id=? for update", Long.class, RequestScope.requireTenantId());
            return null;
        }).when(rbac).lock();
        batch = new PointsBatchService(new MembershipService(db, json, payments, rbac), rbac);
        grantScope(1, 1, EnumSet.of(PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_BATCH));
    }

    @AfterEach
    void clearScope() {
        RequestScope.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void creditsAcrossStoresWithDebtOffsetAndStableReplay() {
        PointsBatchService.BatchResult first = submit(body("batch1", 50, " 活动赠分 ", "2", "1"));
        assertEquals(2, first.getMemberCount());
        assertEquals("100", first.getTotalPoints());
        assertEquals(20L, balance(1, "MOCK", "points"));
        assertEquals(0L, balance(1, "MOCK", "points_debt"));
        assertEquals(50L, balance(2, "MOCK", "points"));
        assertEquals(100L, balance(1, "WECHAT", "points"));
        assertEquals(3L, countLedger());
        assertEquals(2L, db.queryForObject("select count(*) from member_asset_ledger "
            + "where business_key in (?,?) and operator_id=? and store_id=1 and reason=?", Long.class,
            first.getBatchNo() + ":1", first.getBatchNo() + ":2", EMPLOYEE, "活动赠分"));

        db.update("update member set status=0 where id=2");
        grantScope(1, 2, EnumSet.of(PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_BATCH));
        assertEquals(first, submit(body("batch1", 50, "活动赠分", "1", "2")));
        MiniappApiException conflict = assertThrows(MiniappApiException.class,
            () -> submit(body("batch1", 60, "活动赠分", "1", "2")));
        assertEquals(409, conflict.getStatus());
        assertEquals(3L, countLedger());
    }

    @Test
    void refusesInvalidPlansAndRestrictsAccessToTenantWideGrant() {
        JsonNode repeated = body("dup", 1, "活动赠分", "1", "01");
        JsonNode empty = body("empty", 1, "活动赠分");
        JsonNode tooMany = body("oversize", 1, "活动赠分",
            IntStream.rangeClosed(1, 101).mapToObj(Integer::toString).toArray(String[]::new));
        ObjectNode fraction = body("fraction", 1, "活动赠分", "1");
        fraction.put("points", 1.5);
        for (JsonNode invalid : new JsonNode[] {repeated, empty, tooMany, fraction, body("negative", -1, "活动赠分", "1"),
            body("reason", 1, " ", "1"), body("disabled", 1, "活动赠分", "1", "3"),
            body("otherTenant", 1, "活动赠分", "1", "4")}) {
            assertEquals(400, assertThrows(MiniappApiException.class, () -> submit(invalid)).getStatus());
        }
        grantScope(1, 1, EnumSet.of(PermissionAction.MEMBER_READ));
        assertThrows(SecurityException.class, () -> submit(body("noWideGrant", 1, "活动赠分", "1")));
        assertEquals(0L, countLedger());
        assertEquals(0L, db.queryForObject("select count(*) from member_operation_log", Long.class));
    }

    @Test
    void rollsBackEarlierCreditsIfLaterAccountCannotBeCredited() {
        db.update("insert into member_wallet(tenant_id,member_id,environment,points) values(1,2,'MOCK',?)",
            Long.MAX_VALUE);
        assertEquals(400, assertThrows(MiniappApiException.class,
            () -> submit(body("overflow", 50, "活动赠分", "1", "2"))).getStatus());
        assertEquals(0L, balance(1, "MOCK", "points"));
        assertEquals(30L, balance(1, "MOCK", "points_debt"));
        assertEquals(0L, countLedger());
        assertEquals(0L, db.queryForObject("select count(*) from member_operation_log", Long.class));
    }

    @Test
    void concurrentIdenticalRequestsWriteOnlyOneBatch() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<PointsBatchService.BatchResult> task = () -> {
                grantScope(1, 1, EnumSet.of(PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_BATCH));
                try {
                    start.await();
                    return submit(body("retry", 50, "活动赠分", "2", "1"));
                } finally {
                    clearScope();
                }
            };
            Future<PointsBatchService.BatchResult> attemptA = pool.submit(task);
            Future<PointsBatchService.BatchResult> attemptB = pool.submit(task);
            start.countDown();
            assertEquals(attemptA.get(10, TimeUnit.SECONDS), attemptB.get(10, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(3L, countLedger());
        assertEquals(1L, db.queryForObject("select count(*) from member_operation_log", Long.class));
    }

    private void grantScope(long tenant, long store, Set<PermissionAction> tenantPermissions) {
        RequestScope.set(new RequestScope(EMPLOYEE, null, tenant, store, Collections.singleton(store)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RbacRequestContext.begin(request);
        RbacRequestContext.store(new AuthorizationSnapshot(tenant, EMPLOYEE,
            Collections.singletonMap(store, "门店"), Collections.singletonMap(store, EnumSet.allOf(PermissionAction.class)),
            tenantPermissions));
    }

    private ObjectNode body(String requestId, long points, String reason, String... members) {
        ObjectNode body = json.createObjectNode().put("requestId", requestId).put("points", points).put("reason", reason);
        com.fasterxml.jackson.databind.node.ArrayNode ids = body.putArray("memberIds");
        for (String member : members) ids.add(member);
        return body;
    }

    private PointsBatchService.BatchResult submit(JsonNode body) {
        return transactions.execute(status -> batch.grant(body));
    }

    private long countLedger() {
        return db.queryForObject("select count(*) from member_asset_ledger", Long.class);
    }

    private long balance(long member, String environment, String column) {
        return db.queryForObject("select " + column + " from member_wallet where tenant_id=1 and member_id=? and environment=?",
            Long.class, member, environment);
    }
}
