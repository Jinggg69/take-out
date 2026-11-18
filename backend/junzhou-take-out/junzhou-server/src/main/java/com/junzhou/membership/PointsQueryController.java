package com.junzhou.membership;

import com.junzhou.result.Result;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RequireActions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/member/points")
public class PointsQueryController {
    private final PointsQueryService points;

    public PointsQueryController(PointsQueryService points) {
        this.points = points;
    }

    @GetMapping("/overview")
    @RequireActions(PermissionAction.MEMBER_READ)
    public Result<Map<String, Object>> overview(@RequestParam Map<String, String> filters) {
        return Result.success(points.overview(filters));
    }

    @GetMapping("/accounts")
    @RequireActions(PermissionAction.MEMBER_READ)
    public Result<Map<String, Object>> accounts(@RequestParam Map<String, String> filters) {
        return Result.success(points.accounts(filters));
    }

    @GetMapping
    @RequireActions(PermissionAction.MEMBER_READ)
    public Result<Map<String, Object>> ledger(@RequestParam Map<String, String> filters) {
        return Result.success(points.ledger(filters));
    }

    @GetMapping("/export")
    @RequireActions({PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_EXPORT})
    public ResponseEntity<byte[]> export(@RequestParam Map<String, String> filters) {
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=points-ledger.xlsx")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(points.export(filters));
    }
}
