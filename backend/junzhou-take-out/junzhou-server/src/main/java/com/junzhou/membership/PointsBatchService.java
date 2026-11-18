package com.junzhou.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import lombok.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

@Service
public class PointsBatchService {
    private static final int MAX_MEMBERS = 100;
    private static final long MAX_POINTS = 9_007_199_254_740_991L;
    private final MembershipService members;
    private final RbacService rbac;

    public PointsBatchService(MembershipService members, RbacService rbac) {
        this.members = members;
        this.rbac = rbac;
    }

    @Transactional
    public BatchResult grant(JsonNode body) {
        Grant grant = parse(body);
        // The tenant lock serializes first use of a batch number with retries and RBAC mutations.
        rbac.lock();
        members.admin(PermissionAction.MEMBER_READ, true);
        members.admin(PermissionAction.MEMBER_POINT_BATCH, true);
        long tenant = members.tenant();
        long store = members.store();
        String environment = members.env();
        String batchNo = "batchPoint:" + grant.getRequestId();
        int count = grant.getMembers().size();
        String total = Long.toString(grant.getPoints() * count);

        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("memberIds", grant.getMembers());
        canonical.put("points", grant.getPoints());
        canonical.put("reason", grant.getReason());
        String payload = members.encode(canonical);
        Map<String, Object> existing = members.one("select operation_key,payload from member_operation_log where tenant_id=?"
            + " and environment=? and operation_key=? for update", tenant, environment, batchNo);
        if (existing != null) {
            if (!payload.equals(existing.get("payload"))) {
                throw new MiniappApiException(409, "相同请求号已用于其他赠分内容");
            }
            return new BatchResult((String) existing.get("operation_key"), count, total);
        }

        for (long id : grant.getMembers()) {
            try {
                members.active(members.member(id, true));
            } catch (IllegalArgumentException ex) {
                throw invalid("所选会员中有不存在或已停用的记录，本批次未赠分");
            }
        }
        members.jdbc().update("insert into member_operation_log(tenant_id,environment,operation_key,payload)"
            + " values(?,?,?,?)", tenant, environment, batchNo, payload);
        for (long id : grant.getMembers()) {
            try {
                members.creditPoints(id, environment, grant.getPoints(), batchNo + ":" + id, grant.getReason(), store);
            } catch (ArithmeticException ex) {
                throw invalid("赠送后积分超出账户可记录范围，本批次未赠分");
            }
        }
        return new BatchResult(batchNo, count, total);
    }

    private static Grant parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("赠分请求格式无效");
        JsonNode ids = body.path("memberIds");
        if (!ids.isArray() || ids.isEmpty() || ids.size() > MAX_MEMBERS) {
            throw invalid("每次请选择 1 至 100 名会员");
        }
        SortedSet<Long> members = new TreeSet<>();
        for (JsonNode id : ids) {
            if (!id.isTextual() || !id.textValue().matches("[0-9]{1,19}")) throw invalid("会员编号无效");
            long value;
            try {
                value = Long.parseLong(id.textValue());
            } catch (NumberFormatException ex) {
                throw invalid("会员编号无效");
            }
            if (value <= 0) throw invalid("会员编号无效");
            if (!members.add(value)) throw invalid("赠分名单中存在重复会员");
        }
        JsonNode points = body.path("points");
        if (!points.isIntegralNumber() || !points.canConvertToLong() || points.longValue() < 1
            || points.longValue() > MAX_POINTS) {
            throw invalid("每人赠送积分须为有效的正整数");
        }
        JsonNode reason = body.path("reason");
        String text = reason.isTextual() ? reason.textValue().trim() : "";
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 500) throw invalid("请填写 1 至 500 字赠分原因");
        JsonNode request = body.path("requestId");
        if (!request.isTextual() || !request.textValue().matches("[A-Za-z0-9_-]{1,80}")) {
            throw invalid("赠分请求号无效");
        }
        return new Grant(members, points.longValue(), text, request.textValue());
    }

    private static MiniappApiException invalid(String message) {
        return new MiniappApiException(400, message);
    }

    @Value
    private static class Grant {
        SortedSet<Long> members;
        long points;
        String reason;
        String requestId;
    }

    @Value
    public static class BatchResult {
        String batchNo;
        int memberCount;
        String totalPoints;
    }
}
