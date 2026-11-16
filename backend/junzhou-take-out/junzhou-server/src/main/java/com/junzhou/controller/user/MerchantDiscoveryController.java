package com.junzhou.controller.user;

import com.junzhou.annotation.MiniappEntry;
import com.junzhou.dto.MerchantSummaryDTO;
import com.junzhou.result.*;
import com.junzhou.service.MerchantDiscoveryService;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(tags = "小程序公开商家发现")
@RequestMapping("/user/merchants")
@MiniappEntry(MiniappEntry.Scope.PUBLIC)
public class MerchantDiscoveryController {
    private final MerchantDiscoveryService service;
    public MerchantDiscoveryController(MerchantDiscoveryService service) { this.service = service; }
    @GetMapping
    public Result<PageResult> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
        return Result.success(service.merchants(keyword,page,pageSize));
    }
    @GetMapping("/{merchantId}")
    public Result<MerchantSummaryDTO> merchant(@PathVariable String merchantId) { return Result.success(service.merchant(merchantId)); }
    @GetMapping("/{merchantId}/stores")
    public Result<PageResult> stores(@PathVariable String merchantId,@RequestParam(required=false) String keyword,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
        return Result.success(service.stores(merchantId,keyword,page,pageSize));
    }
}
