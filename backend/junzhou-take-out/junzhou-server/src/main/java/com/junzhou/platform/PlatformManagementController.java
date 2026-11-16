package com.junzhou.platform;

import com.junzhou.result.PageResult;
import com.junzhou.result.Result;
import lombok.Data;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/platform")
public class PlatformManagementController {
    private final PlatformManagementService service;
    public PlatformManagementController(PlatformManagementService service) {this.service=service;}
    @GetMapping("/tenants") public Result<PageResult> tenants(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) String keyword) {PlatformContext.requireAdminId();return Result.success(service.tenants(page,pageSize,keyword));}
    @GetMapping("/stores") public Result<PageResult> stores(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) String keyword,@RequestParam(required=false) Long tenantId) {PlatformContext.requireAdminId();return Result.success(service.stores(page,pageSize,keyword,tenantId));}
    @PostMapping("/tenants") public Result<Long> createTenant(@RequestBody Map<String,Object> body) {PlatformContext.requireAdminId();return Result.success(service.createTenant(body));}
    @PostMapping("/stores") public Result<Long> createStore(@RequestBody Map<String,Object> body) {PlatformContext.requireAdminId();return Result.success(service.createStore(body));}
    @PutMapping("/tenants/{id}") public Result<Void> updateTenant(@PathVariable Long id,@RequestBody Map<String,Object> body) {PlatformContext.requireAdminId();service.updateTenant(id,body);return Result.success();}
    @PutMapping("/stores/{id}") public Result<Void> updateStore(@PathVariable Long id,@RequestBody Map<String,Object> body) {PlatformContext.requireAdminId();service.updateStore(id,body);return Result.success();}
    @DeleteMapping("/tenants/{id}") public Result<Void> deleteTenant(@PathVariable Long id) {PlatformContext.requireAdminId();service.deleteTenant(id);return Result.success();}
    @DeleteMapping("/stores/{id}") public Result<Void> deleteStore(@PathVariable Long id) {PlatformContext.requireAdminId();service.deleteStore(id);return Result.success();}
    @PutMapping("/tenants/{id}/status") public Result<Void> tenantStatus(@PathVariable Long id,@RequestBody StatusBody body) {PlatformContext.requireAdminId();service.tenantStatus(id,body.getStatus());return Result.success();}
    @PutMapping("/stores/{id}/status") public Result<Void> storeStatus(@PathVariable Long id,@RequestBody StatusBody body) {PlatformContext.requireAdminId();service.storeStatus(id,body.getStatus());return Result.success();}
    @ExceptionHandler(DuplicateKeyException.class) public Result<Void> duplicate() {return Result.error("编码或管理员用户名已存在，请使用其他值");}
    @Data public static class StatusBody { private Integer status; }
}
