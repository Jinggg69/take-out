package com.junzhou.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.junzhou.decoration.HomeDecorationService;
import com.junzhou.decoration.HomeDecorationService.AdminState;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.result.Result;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RequireActions;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/decoration/home")
public class HomeDecorationController {
    private final HomeDecorationService service;

    public HomeDecorationController(HomeDecorationService service) { this.service = service; }

    @GetMapping
    @RequireActions(PermissionAction.DECORATION_MANAGE)
    public Result<AdminState> read() { return Result.success(service.read()); }

    @PutMapping
    @RequireActions(PermissionAction.DECORATION_MANAGE)
    public Result<AdminState> save(@RequestBody JsonNode body) {
        return Result.success(service.save(version(body), body.path("config")));
    }

    @PostMapping("/publish")
    @RequireActions(PermissionAction.DECORATION_MANAGE)
    public Result<AdminState> publish(@RequestBody JsonNode body) {
        return Result.success(service.publish(version(body)));
    }

    @PostMapping("/reset")
    @RequireActions(PermissionAction.DECORATION_MANAGE)
    public Result<AdminState> reset(@RequestBody JsonNode body) {
        return Result.success(service.reset(version(body)));
    }

    private static long version(JsonNode body) {
        JsonNode version = body.path("version");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0) {
            throw new MiniappApiException(400, "装修版本无效，请重新加载");
        }
        return version.longValue();
    }
}
