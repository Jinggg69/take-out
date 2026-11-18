package com.junzhou.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.junzhou.context.RequestScope;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.ratelimit.RateLimitGuard;
import com.junzhou.security.AuthorizationSnapshot;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RbacRequestContext;
import com.junzhou.service.PaymentAccountService;
import com.junzhou.service.RbacService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PointsQueryServiceTest {
    private PointsQueryService queries;
    private PaymentAccountService payments;
    private RateLimitGuard rateLimits;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE member (id BIGINT PRIMARY KEY, tenant_id BIGINT, member_no VARCHAR(50), name VARCHAR(100), phone VARCHAR(32), status INT, level_id BIGINT)");
        jdbc.execute("CREATE TABLE member_wallet (member_id BIGINT, tenant_id BIGINT, environment VARCHAR(10), points BIGINT, points_held BIGINT, points_debt BIGINT, level_id BIGINT)");
        jdbc.execute("CREATE TABLE member_level (id BIGINT, tenant_id BIGINT, name VARCHAR(80))");
        jdbc.execute("CREATE TABLE member_tag_relation (tenant_id BIGINT, member_id BIGINT, tag_id BIGINT)");
        jdbc.execute("CREATE TABLE store (id BIGINT, tenant_id BIGINT, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE employee (id BIGINT, tenant_id BIGINT, name VARCHAR(100))");
        jdbc.execute("CREATE TABLE member_asset_ledger (id BIGINT PRIMARY KEY, tenant_id BIGINT, member_id BIGINT, store_id BIGINT, environment VARCHAR(10), asset VARCHAR(20), delta BIGINT, before_amount BIGINT, after_amount BIGINT, business_key VARCHAR(160), reason VARCHAR(500), operator_id BIGINT, create_time TIMESTAMP)");
        jdbc.update("INSERT INTO member VALUES (1,1,'M001','甲','13800138000',1,1),(2,1,'M002','乙','',0,NULL),(3,2,'M003','丙','',1,NULL)");
        jdbc.update("INSERT INTO member_wallet VALUES (1,1,'MOCK',90,15,4,2),(1,1,'WECHAT',600,10,0,NULL),(3,2,'MOCK',999,0,0,NULL)");
        jdbc.update("INSERT INTO member_level VALUES (1,1,'普通会员'),(2,1,'银卡'),(2,2,'其它租户等级')");
        jdbc.update("INSERT INTO member_tag_relation VALUES (1,1,31),(2,3,31)");
        jdbc.update("INSERT INTO store VALUES (10,1,'主店'),(11,1,'分店'),(20,2,'其它商户门店')");
        jdbc.update("INSERT INTO employee VALUES (8,1,'操作员'),(8,2,'其它商户操作员')");
        jdbc.update("INSERT INTO member_asset_ledger VALUES "
            + "(1,1,1,10,'MOCK','POINTS',20,70,90,'orderEarn:20','消费积分',NULL,'2026-11-18 00:00:00'),"
            + "(2,1,1,11,'MOCK','POINTS',-9,90,81,'claim:1:5:a','积分兑换优惠券',NULL,'2026-11-18 12:00:00'),"
            + "(3,1,1,11,'MOCK','POINTS_DEBT',-4,8,4,'batchPoint:b:1:debt','后续积分冲抵待扣',8,'2026-11-18 23:59:59'),"
            + "(4,1,1,10,'MOCK','POINTS',5,81,86,'batchPoint:b:1','=有效文本',8,'2026-11-18 23:59:59'),"
            + "(5,1,1,10,'WECHAT','POINTS',111,0,111,'orderEarn:90','消费积分',NULL,'2026-11-18 12:00:00'),"
            + "(6,2,3,20,'MOCK','POINTS',300,0,300,'orderEarn:50','消费积分',NULL,'2026-11-18 12:00:00'),"
            + "(7,1,1,10,'MOCK','POINTS',200,86,286,'orderPaid:40','订单消费',NULL,'2026-11-19 00:00:00')");
        payments = mock(PaymentAccountService.class);
        when(payments.channelForNewOrders()).thenReturn("MOCK");
        rateLimits = mock(RateLimitGuard.class);
        queries = new PointsQueryService(new MembershipService(jdbc, new ObjectMapper(), payments, mock(RbacService.class)), rateLimits);
        authorize(EnumSet.of(PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_EXPORT));
    }

    @AfterEach
    void clearContext() {
        RequestScope.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void overviewUsesCurrentWalletAndActualPointRowsInTenantAndEnvironment() {
        Map<String, Object> overview = queries.overview(params("startDate", "2026-11-18", "endDate", "2026-11-18"));
        assertEquals("MOCK", overview.get("environment"));
        assertValue("75", overview, "availablePoints");
        assertValue("15", overview, "frozenPoints");
        assertValue("4", overview, "pointsDebt");
        assertValue("25", overview, "creditedPoints");
        assertValue("9", overview, "debitedPoints");

        when(payments.channelForNewOrders()).thenReturn("WECHAT");
        overview = queries.overview(params("startDate", "2026-11-18", "endDate", "2026-11-18"));
        assertValue("590", overview, "availablePoints");
        assertValue("111", overview, "creditedPoints");
    }

    @Test
    void accountsFilterOnCurrentEnvironmentLevelAndTenantTag() {
        List<Map<String, Object>> accounts = records(queries.accounts(params("keyword", "13800", "levelId", "2", "tagId", "31", "status", "1")));
        assertEquals(1, accounts.size());
        Map<String, Object> account = accounts.get(0);
        assertEquals("1", account.get("id"));
        assertEquals("银卡", account.get("levelName"));
        assertValue("75", account, "availablePoints");
        assertValue("15", account, "frozenPoints");
        assertValue("4", account, "pointsDebt");
        assertEquals("M002", records(queries.accounts(params("status", "0"))).get(0).get("memberNo"));
    }

    @Test
    void historyAndExportShareFiltersAndKeepAmountsAndTextSafe() throws Exception {
        Map<String, String> filter = params("startDate", "2026-11-18", "endDate", "2026-11-18", "businessNo", "batchPoint:b", "storeId", "10", "source", "BATCH_GRANT");
        List<Map<String, Object>> ledger = records(queries.ledger(filter));
        assertEquals(1, ledger.size());
        assertEquals("BATCH_GRANT", ledger.get(0).get("source"));
        assertEquals("batchPoint:b", ledger.get(0).get("batchNo"));
        assertEquals("操作员", ledger.get(0).get("operatorName"));
        assertEquals("主店", ledger.get(0).get("storeName"));
        assertValue("5", ledger.get(0), "delta");
        assertEquals("claim:1:5:a", records(queries.ledger(params("source", "COUPON_EXCHANGE"))).get(0).get("businessNo"));
        assertEquals(1, records(queries.ledger(params("assetType", "POINTS_DEBT"))).size());
        assertEquals(2, records(queries.ledger(params("startDate", "2026-11-18 23:59:59", "endDate", "2026-11-18 23:59:59"))).size());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(queries.export(filter)))) {
            assertEquals(1, workbook.getSheetAt(0).getLastRowNum());
            boolean matched = false;
            for (org.apache.poi.ss.usermodel.Cell cell : workbook.getSheetAt(0).getRow(1)) {
                if (cell.getCellTypeEnum() == CellType.STRING && "=有效文本".equals(cell.getStringCellValue())) matched = true;
            }
            assertTrue(matched);
            assertFalse(workbook.getSheetAt(0).getRow(0).toString().contains("手机号"));
        }
        verify(rateLimits).reportExport();
    }

    @Test
    void requiresTenantWideReadAndSeparateExportPermissionAndLimitsDates() {
        authorize(EnumSet.of(PermissionAction.MEMBER_READ));
        assertThrows(SecurityException.class, () -> queries.export(params("startDate", "2026-10-01", "endDate", "2026-10-31")));
        authorize(EnumSet.of(PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_EXPORT));
        MiniappApiException range = assertThrows(MiniappApiException.class, () ->
            queries.export(params("startDate", "2026-10-01", "endDate", "2026-11-01")));
        assertEquals(400, range.getStatus());
        assertThrows(MiniappApiException.class, () -> queries.ledger(params("source", "UNKNOWN")));
        verifyNoInteractions(rateLimits);
        authorize(EnumSet.noneOf(PermissionAction.class));
        assertThrows(SecurityException.class, () -> queries.overview(Collections.emptyMap()));
        assertThrows(SecurityException.class, () -> queries.accounts(Collections.emptyMap()));
        assertThrows(SecurityException.class, () -> queries.ledger(Collections.emptyMap()));
    }

    private void authorize(EnumSet<PermissionAction> wide) {
        RequestScope.set(new RequestScope(8L, null, 1L, 10L, Collections.singleton(10L)));
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RbacRequestContext.begin(request);
        RbacRequestContext.store(new AuthorizationSnapshot(1L, 8L, Collections.singletonMap(10L, "主店"),
            Collections.singletonMap(10L, EnumSet.copyOf(Arrays.asList(PermissionAction.MEMBER_READ,
                PermissionAction.MEMBER_POINT_EXPORT))), wide));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> page) {
        return (List<Map<String, Object>>) page.get("records");
    }

    private static void assertValue(String expected, Map<String, Object> values, String key) {
        assertEquals(expected, String.valueOf(values.get(key)), key);
    }

    private static Map<String, String> params(String... pairs) {
        Map<String, String> filter = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) filter.put(pairs[i], pairs[i + 1]);
        return filter;
    }
}
