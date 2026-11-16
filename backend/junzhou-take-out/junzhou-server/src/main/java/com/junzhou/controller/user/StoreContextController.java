package com.junzhou.controller.user;

import com.junzhou.annotation.MiniappEntry;
import com.junzhou.dto.StoreContextDTO;
import com.junzhou.result.Result;
import com.junzhou.service.MerchantDiscoveryService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "小程序公开门店上下文")
@MiniappEntry(MiniappEntry.Scope.PUBLIC)
@RequestMapping("/user/stores")
public class StoreContextController {
    private final MerchantDiscoveryService service;
    public StoreContextController(MerchantDiscoveryService service) { this.service = service; }
    @GetMapping("/{storeId}/context")
    public Result<StoreContextDTO> context(@PathVariable String storeId) { return Result.success(service.context(storeId)); }
}
