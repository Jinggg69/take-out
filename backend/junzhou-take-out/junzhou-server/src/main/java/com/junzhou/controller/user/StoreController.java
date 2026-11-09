package com.junzhou.controller.user;

import com.junzhou.entity.StoreSettings;
import com.junzhou.result.Result;
import com.junzhou.service.StoreSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController("userStoreController")
@RequestMapping("/user/stores")
public class StoreController {
    @Autowired private StoreSettingsService settings;
    @GetMapping
    public Result<List<StoreSettings>> list() { return Result.success(settings.stores()); }
}
