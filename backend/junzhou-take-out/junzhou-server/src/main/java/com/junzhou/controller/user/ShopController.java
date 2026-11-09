package com.junzhou.controller.user;

import com.junzhou.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    @Autowired private com.junzhou.service.StoreSettingsService settings;


    /**
     * 获取店铺营业状态
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus() {
        return Result.success(settings.current().getOpen() ? 1 : 0);
    }
    @GetMapping("/getMerchantInfo")
    public Result<com.junzhou.entity.StoreSettings> merchant() {
        return Result.success(settings.current());
    }
}
