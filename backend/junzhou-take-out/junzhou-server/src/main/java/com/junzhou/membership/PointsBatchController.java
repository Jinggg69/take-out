package com.junzhou.membership;

import com.fasterxml.jackson.databind.JsonNode;
import com.junzhou.result.Result;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RequireActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/member/points")
public class PointsBatchController {
    private final PointsBatchService batch;

    public PointsBatchController(PointsBatchService batch) {
        this.batch = batch;
    }

    @PostMapping("/batch")
    @RequireActions({PermissionAction.MEMBER_READ, PermissionAction.MEMBER_POINT_BATCH})
    public Result<PointsBatchService.BatchResult> grant(@RequestBody JsonNode body) {
        return Result.success(batch.grant(body));
    }
}
