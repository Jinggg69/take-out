package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.ReportMapper;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import com.junzhou.test.support.CountingReportMapper;
import com.junzhou.test.support.InMemoryRedis;
import com.junzhou.test.support.ReportTestDatabase;
import com.junzhou.test.support.ReportTestSupport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Export must always read the database and never reuse a cached chart snapshot. */
class ReportCacheExportTest {
    private ReportTestDatabase db;
    private CountingReportMapper mapper;
    private RbacService rbac;
    private InMemoryRedis redis;
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        db = ReportTestDatabase.create();
        mapper = new CountingReportMapper(db.mapper());
        rbac = mock(RbacService.class);
        when(rbac.permits(anyLong(), anyLong(), anyLong(), any(PermissionAction.class))).thenReturn(true);
        redis = new InMemoryRedis();
        service = new ReportServiceImpl(mapper, rbac,
                new ReportResultCache(redis.template, ReportTestSupport.properties(true)), ReportTestSupport.fixedToday());
        RequestScope.set(new RequestScope(1L, null, 1L, 1L, new HashSet<>(Arrays.asList(1L, 2L))));
    }

    @AfterEach
    void cleanup() {
        RequestScope.clear();
        if (db != null) db.close();
    }

    private ReportFilter filter() {
        return service.filter("2026-11-01", "2026-11-03", null, false);
    }

    @Test
    void exportReadsTheLatestDatabaseWhileTheChartKeepsItsValidSnapshot() throws Exception {
        ReportFilter filter = filter();
        assertEquals("0.00,30.00,0.00", service.turnover(filter).get("turnoverList"));
        assertEquals(1, redis.reads.get());
        assertEquals(1, redis.writes.get());

        // A payment lands after the chart was cached.
        db.jdbc().update("insert into orders values(5,1,1,'e','2026-11-02 09:00','2026-11-02 12:00',null,null,40,3,5,'DELIVERY')");

        byte[] workbook = service.export(filter);
        int readsAfterExport = redis.reads.get();
        assertEquals(1, readsAfterExport, "export must not read a report cache key");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            Sheet daily = wb.getSheet("Daily");
            assertEquals("2026-11-01", daily.getRow(1).getCell(0).getStringCellValue());
            assertEquals(0.0, daily.getRow(1).getCell(1).getNumericCellValue(), 1e-9);
            assertEquals(70.0, daily.getRow(2).getCell(1).getNumericCellValue(), 1e-9,
                    "export must see the newly paid 40 on top of the cached 30");
        }

        assertEquals("0.00,30.00,0.00", service.turnover(filter).get("turnoverList"),
                "the chart may keep its value until the TTL expires");
        assertEquals(readsAfterExport + 1, redis.reads.get(), "only the chart read touches the cache");
        assertEquals(1, redis.writes.get(), "export must not write a report cache key");
    }

    @Test
    void exportStillRequiresReadAndExportAuthorityWithoutTouchingTheCache() {
        ReportFilter filter = filter();
        service.turnover(filter);
        int reads = redis.reads.get();
        int sqlCalls = mapper.total();

        when(rbac.permits(1L, 1L, 1L, PermissionAction.REPORT_EXPORT)).thenReturn(false);
        assertThrows(SecurityException.class, () -> service.export(filter));

        assertEquals(reads, redis.reads.get());
        assertEquals(sqlCalls, mapper.total());
    }

    @Test
    void exportRejectsMoreThanFiftyThousandEventsWithoutCacheAccess() {
        ReportMapper huge = mock(ReportMapper.class);
        List<ReportRow> events = new ArrayList<>();
        for (int i = 0; i < 50001; i++) events.add(new ReportRow());
        when(huge.ledger(any())).thenReturn(events);
        when(huge.itemDetails(any())).thenReturn(Collections.emptyList());
        ReportServiceImpl oversized = new ReportServiceImpl(huge, rbac,
                new ReportResultCache(redis.template, ReportTestSupport.properties(true)), ReportTestSupport.fixedToday());
        ReportFilter filter = new ReportFilter(1L, Collections.singletonList(1L),
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 11, 4, 0, 0));

        assertThrows(OrderBusinessException.class, () -> oversized.export(filter));
        assertEquals(0, redis.reads.get());
        assertEquals(0, redis.writes.get());
    }

    @Test
    void exportKeepsItsFourSheetsColumnsAndDetailRows() throws Exception {
        ReportFilter filter = filter();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.export(filter)))) {
            assertEquals(4, wb.getNumberOfSheets());
            assertEquals("Daily", wb.getSheetAt(0).getSheetName());
            assertEquals("Items", wb.getSheetAt(1).getSheetName());
            assertEquals("Events", wb.getSheetAt(2).getSheetName());
            assertEquals("Order items", wb.getSheetAt(3).getSheetName());
            assertEquals("Asia/Shanghai date", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Net", wb.getSheetAt(0).getRow(0).getCell(3).getStringCellValue());

            Sheet events = wb.getSheetAt(2);
            assertTrue(events.getLastRowNum() > 0);
            Row firstEvent = events.getRow(1);
            assertNotNull(firstEvent.getCell(5).getStringCellValue());
            assertTrue(firstEvent.getCell(5).getStringCellValue().matches("[A-Z_]+"));

            Sheet itemDetails = wb.getSheetAt(3);
            assertTrue(itemDetails.getLastRowNum() > 0);
            assertEquals("Payment time Asia/Shanghai", itemDetails.getRow(0).getCell(4).getStringCellValue());
        }

        // Nothing was cached along the export path.
        assertEquals(0, redis.reads.get());
        assertEquals(0, redis.writes.get());
    }
}
