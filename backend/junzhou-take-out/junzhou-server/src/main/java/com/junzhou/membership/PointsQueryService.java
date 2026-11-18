package com.junzhou.membership;

import com.junzhou.exception.MiniappApiException;
import com.junzhou.ratelimit.RateLimitGuard;
import com.junzhou.security.PermissionAction;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PointsQueryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int EXPORT_MAX_DAYS = 31;
    private static final int EXPORT_MAX_ROWS = 10_000;
    private static final long JS_MAX_SAFE_INTEGER = 9_007_199_254_740_991L;
    private static final String BATCH_PREFIX = "batchPoint:";
    private static final String LEDGER_SOURCE = "CASE"
        + " WHEN h.business_key LIKE 'orderEarn:%' THEN 'ORDER_EARN'"
        + " WHEN h.business_key LIKE 'orderPaid:%' THEN 'ORDER_DEDUCT'"
        + " WHEN h.business_key LIKE 'orderRefund:%' OR h.business_key LIKE 'orderEarnReversal:%'"
        + " OR h.business_key LIKE 'orderEarnDebt:%' THEN 'ORDER_REFUND'"
        + " WHEN h.business_key LIKE 'claim:%' AND h.reason='积分兑换优惠券' THEN 'COUPON_EXCHANGE'"
        + " WHEN h.business_key LIKE 'adjust:%' THEN 'MANUAL'"
        + " WHEN h.business_key LIKE 'batchPoint:%' THEN 'BATCH_GRANT'"
        + " ELSE 'OTHER' END";
    private static final String LEDGER_COLUMNS = "SELECT h.*,m.member_no,m.name AS member_name,"
        + "s.name AS store_name,e.name AS operator_name," + LEDGER_SOURCE + " AS source";
    private static final String LEDGER_ORDER = " ORDER BY h.id DESC";

    private final MembershipService members;
    private final RateLimitGuard rateLimits;

    public PointsQueryService(MembershipService members, RateLimitGuard rateLimits) {
        this.members = members;
        this.rateLimits = rateLimits;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> overview(Map<String, String> filters) {
        members.admin(PermissionAction.MEMBER_READ, true);
        long tenant = members.tenant();
        String environment = members.env();
        DateRange range = dates(filters);
        if (range.from == null && range.until == null) {
            LocalDate today = LocalDate.now(BUSINESS_ZONE);
            range = new DateRange(today.minusDays(29).atStartOfDay(), today.plusDays(1).atStartOfDay());
        }
        range.requireBoth();
        Map<String, Object> balances = members.one("SELECT COALESCE(SUM(points-points_held),0) AS available_points,"
            + "COALESCE(SUM(points_held),0) AS frozen_points,COALESCE(SUM(points_debt),0) AS points_debt"
            + " FROM member_wallet WHERE tenant_id=? AND environment=?", tenant, environment);
        Map<String, Object> movements = members.one("SELECT COALESCE(SUM(CASE WHEN delta>0 THEN delta ELSE 0 END),0) AS credited_points,"
            + "COALESCE(SUM(CASE WHEN delta<0 THEN -CAST(delta AS DECIMAL(20,0)) ELSE 0 END),0) AS debited_points"
            + " FROM member_asset_ledger WHERE tenant_id=? AND environment=? AND asset='POINTS'"
            + " AND create_time>=? AND create_time<?", tenant, environment, Timestamp.valueOf(range.from), Timestamp.valueOf(range.until));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environment);
        result.put("availablePoints", balances.get("available_points").toString());
        result.put("frozenPoints", balances.get("frozen_points").toString());
        result.put("pointsDebt", balances.get("points_debt").toString());
        result.put("creditedPoints", movements.get("credited_points").toString());
        result.put("debitedPoints", movements.get("debited_points").toString());
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> accounts(Map<String, String> filters) {
        members.admin(PermissionAction.MEMBER_READ, true);
        Query query = new Query(" FROM member m"
            + " LEFT JOIN member_wallet w ON w.tenant_id=m.tenant_id AND w.member_id=m.id AND w.environment=?"
            + " LEFT JOIN member_level l ON l.tenant_id=m.tenant_id AND l.id=COALESCE(w.level_id,m.level_id)"
            + " WHERE m.tenant_id=?", members.env(), members.tenant());
        memberKeyword(query, filters);
        query.condition(" AND COALESCE(w.level_id,m.level_id)=?", id(filters, "levelId"));
        query.condition(" AND EXISTS(SELECT 1 FROM member_tag_relation tr"
            + " WHERE tr.tenant_id=m.tenant_id AND tr.member_id=m.id AND tr.tag_id=?)", id(filters, "tagId"));
        String status = value(filters, "status");
        if (status != null && !"0".equals(status) && !"1".equals(status)) throw invalid("会员状态无效");
        query.condition(" AND m.status=?", status);
        String columns = "SELECT m.id,m.member_no,m.name,m.phone,m.status,COALESCE(l.name,'普通会员') AS level_name,"
            + "COALESCE(w.points-w.points_held,0) AS available_points,COALESCE(w.points_held,0) AS frozen_points,"
            + "COALESCE(w.points_debt,0) AS points_debt";
        return members.queryPage(columns, query.sql.toString(), " ORDER BY m.id DESC", query.args,
            pagination(filters), this::accountRow);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> ledger(Map<String, String> filters) {
        members.admin(PermissionAction.MEMBER_READ, true);
        Query query = ledgerQuery(filters, dates(filters));
        return members.queryPage(LEDGER_COLUMNS, query.sql.toString(), LEDGER_ORDER, query.args,
            pagination(filters), this::ledgerRow);
    }

    public byte[] export(Map<String, String> filters) {
        members.admin(PermissionAction.MEMBER_READ, true);
        members.admin(PermissionAction.MEMBER_POINT_EXPORT, true);
        DateRange range = dates(filters);
        range.requireBoth();
        if (ChronoUnit.DAYS.between(range.from.toLocalDate(), range.until.minusNanos(1).toLocalDate()) >= EXPORT_MAX_DAYS) {
            throw invalid("单次最多导出31天的积分流水，请缩小日期范围");
        }
        Query query = ledgerQuery(filters, range);
        rateLimits.reportExport();
        List<Map<String, Object>> rows = members.rows(LEDGER_COLUMNS + query.sql + LEDGER_ORDER + " LIMIT " + (EXPORT_MAX_ROWS + 1),
            query.args.toArray());
        if (rows.size() > EXPORT_MAX_ROWS) throw invalid("积分流水超过10000条，请缩小筛选范围");
        return workbook(rows);
    }

    private Query ledgerQuery(Map<String, String> filters, DateRange range) {
        Query query = new Query(" FROM member_asset_ledger h"
            + " JOIN member m ON m.tenant_id=h.tenant_id AND m.id=h.member_id"
            + " LEFT JOIN store s ON s.tenant_id=h.tenant_id AND s.id=h.store_id"
            + " LEFT JOIN employee e ON e.tenant_id=h.tenant_id AND e.id=h.operator_id"
            + " WHERE h.tenant_id=? AND h.environment=? AND h.asset IN ('POINTS','POINTS_DEBT')", members.tenant(), members.env());
        Long memberId = id(filters, "memberId");
        if (memberId != null) {
            try {
                members.member(memberId, false);
            } catch (IllegalArgumentException ex) {
                throw invalid("会员不存在");
            }
        }
        query.condition(" AND h.member_id=?", memberId);
        query.condition(" AND h.store_id=?", id(filters, "storeId"));
        query.condition(" AND h.create_time>=?", range.from == null ? null : Timestamp.valueOf(range.from));
        query.condition(" AND h.create_time<?", range.until == null ? null : Timestamp.valueOf(range.until));
        memberKeyword(query, filters);
        query.condition(" AND h.business_key LIKE ? ESCAPE '!'", contains(filters, "businessNo", 160));
        String asset = value(filters, "assetType");
        if (asset != null && !"POINTS".equals(asset) && !"POINTS_DEBT".equals(asset)) throw invalid("积分资产类型无效");
        query.condition(" AND h.asset=?", asset);
        String source = value(filters, "source");
        if (source != null) {
            try {
                LedgerSource.valueOf(source);
            } catch (IllegalArgumentException ex) {
                throw invalid("积分来源无效");
            }
            query.condition(" AND (" + LEDGER_SOURCE + ")=?", source);
        }
        return query;
    }

    private Map<String, Object> accountRow(Map<String, Object> row) {
        Map<String, Object> result = members.camel(row);
        for (String key : new String[]{"availablePoints", "frozenPoints", "pointsDebt"}) {
            result.put(key, precisionSafe(result.get(key)));
        }
        return result;
    }

    private Map<String, Object> ledgerRow(Map<String, Object> row) {
        Map<String, Object> result = members.camel(row);
        result.put("beforeValue", precisionSafe(row.get("before_amount")));
        result.put("afterValue", precisionSafe(row.get("after_amount")));
        result.put("delta", precisionSafe(row.get("delta")));
        result.put("assetType", row.get("asset"));
        result.put("businessNo", row.get("business_key"));
        if ("BATCH_GRANT".equals(row.get("source"))) {
            String key = row.get("business_key").toString();
            int end = key.indexOf(':', BATCH_PREFIX.length());
            result.put("batchNo", end < 0 ? key : key.substring(0, end));
        }
        if (row.get("operator_id") == null) result.put("operatorName", "系统");
        return result;
    }

    private static Object precisionSafe(Object amount) {
        long n = ((Number) amount).longValue();
        return n < -JS_MAX_SAFE_INTEGER || n > JS_MAX_SAFE_INTEGER ? Long.toString(n) : n;
    }

    private byte[] workbook(List<Map<String, Object>> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("积分流水");
            excelRow(sheet, 0, "时间（北京时间）", "会员号", "来源", "资产类型", "变动积分", "变动前", "变动后",
                "业务号", "批次号", "原因", "操作人", "门店", "环境");
            int number = 1;
            for (Map<String, Object> raw : rows) {
                Map<String, Object> row = ledgerRow(raw);
                String environment = "MOCK".equals(row.get("environment")) ? "模拟支付" : "微信支付";
                String asset = "POINTS_DEBT".equals(row.get("assetType")) ? "待扣积分" : "积分";
                excelRow(sheet, number++, row.get("createTime"), row.get("memberNo"), LedgerSource.valueOf(row.get("source").toString()).label,
                    asset, row.get("delta"), row.get("beforeValue"), row.get("afterValue"), row.get("businessNo"), row.get("batchNo"),
                    row.get("reason"), row.get("operatorName"), row.get("storeName"), environment);
            }
            sheet.createFreezePane(0, 1);
            for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) sheet.setColumnWidth(i, 22 * 256);
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成积分流水文件失败", ex);
        }
    }

    private static void excelRow(Sheet sheet, int index, Object... values) {
        Row row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value instanceof Long && (long) value >= -999_999_999_999_999L && (long) value <= 999_999_999_999_999L) {
                row.createCell(i).setCellValue(((Long) value).doubleValue());
            } else {
                row.createCell(i).setCellValue(value == null ? "" : value.toString());
            }
        }
    }

    private static void memberKeyword(Query query, Map<String, String> filters) {
        String term = contains(filters, "keyword", 100);
        if (term != null) query.add(" AND (m.member_no LIKE ? ESCAPE '!' OR m.name LIKE ? ESCAPE '!' OR m.phone LIKE ? ESCAPE '!')",
            term, term, term);
    }

    private static String contains(Map<String, String> filters, String key, int maxLength) {
        String text = value(filters, key);
        if (text == null) return null;
        if (text.length() > maxLength) throw invalid("搜索内容过长");
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static Long id(Map<String, String> filters, String key) {
        String text = value(filters, key);
        if (text == null) return null;
        try {
            long id = Long.parseLong(text);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // An invalid or overflowing ID cannot be passed to SQL.
        }
        throw invalid("筛选编号无效");
    }

    private static Map<String, String> pagination(Map<String, String> filters) {
        Map<String, String> page = new LinkedHashMap<>();
        for (String key : new String[]{"page", "pageSize"}) {
            String text = value(filters, key);
            if (text == null) continue;
            try {
                page.put(key, Integer.toString(Integer.parseInt(text)));
            } catch (NumberFormatException ex) {
                throw invalid("分页参数无效");
            }
        }
        return page;
    }

    private static String value(Map<String, String> filters, String key) {
        String text = filters.get(key);
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    private static DateRange dates(Map<String, String> filters) {
        return new DateRange(date(value(filters, "startDate"), false), date(value(filters, "endDate"), true));
    }

    private static LocalDateTime date(String value, boolean end) {
        if (value == null) return null;
        try {
            if (value.length() == 10) return LocalDate.parse(value).plusDays(end ? 1 : 0).atStartOfDay();
            return LocalDateTime.parse(value.replace(' ', 'T')).plusSeconds(end ? 1 : 0);
        } catch (DateTimeException ex) {
            throw invalid("日期格式无效");
        }
    }

    private static MiniappApiException invalid(String message) {
        return new MiniappApiException(400, message);
    }

    private enum LedgerSource {
        ORDER_EARN("订单赠分"), ORDER_DEDUCT("订单抵扣"), ORDER_REFUND("订单退款"),
        COUPON_EXCHANGE("积分兑券"), MANUAL("人工调整"), BATCH_GRANT("批量赠分"), OTHER("其他");

        final String label;

        LedgerSource(String label) {
            this.label = label;
        }
    }

    private static final class DateRange {
        final LocalDateTime from;
        final LocalDateTime until;

        DateRange(LocalDateTime from, LocalDateTime until) {
            if (from != null && until != null && !from.isBefore(until)) throw invalid("开始日期不能晚于结束日期");
            this.from = from;
            this.until = until;
        }

        void requireBoth() {
            if (from == null || until == null) throw invalid("请选择开始和结束日期");
        }
    }

    private static final class Query {
        final StringBuilder sql = new StringBuilder();
        final List<Object> args = new ArrayList<>();

        Query(String fragment, Object... parameters) {
            add(fragment, parameters);
        }

        void condition(String fragment, Object value) {
            if (value != null) add(fragment, value);
        }

        void add(String fragment, Object... parameters) {
            sql.append(fragment);
            Collections.addAll(args, parameters);
        }
    }
}
