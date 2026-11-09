package com.junzhou.controller.admin;

import com.junzhou.entity.StoreSettings;
import com.junzhou.entity.StoreMenuItem;
import com.junzhou.result.Result;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RequireActions;
import com.junzhou.service.StoreSettingsService;
import com.junzhou.service.StoreMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/store")
public class StoreController {
    @Autowired private StoreSettingsService settings;
    @Autowired private StoreMenuService menu;

    @GetMapping("/settings")
    @RequireActions({PermissionAction.SHOP_READ})
    public Result<StoreSettings> settings() { return Result.success(settings.current()); }

    @PutMapping("/settings")
    @RequireActions({PermissionAction.SHOP_UPDATE})
    public Result<Void> save(@RequestBody StoreSettings value) {
        settings.save(value);
        return Result.success();
    }

    @GetMapping("/menu")
    @RequireActions({PermissionAction.SHOP_READ})
    public Result<List<StoreMenuItem>> menu(@RequestParam String type) { return Result.success(menu.list(type)); }

    @PutMapping("/menu")
    @RequireActions({PermissionAction.SHOP_UPDATE})
    public Result<Void> saveMenu(@RequestParam String type, @RequestBody StoreMenuItem value) {
        menu.save(type,value);
        return Result.success();
    }
}
