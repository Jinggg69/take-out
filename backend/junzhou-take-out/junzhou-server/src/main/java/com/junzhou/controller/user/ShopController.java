package com.junzhou.controller.user;

import com.junzhou.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {
    private static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取店铺营业状态
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(SHOP_STATUS_KEY);
        if (status == null) {
            status = 1; // 默认状态为营业中
        }
        log.info("获取店铺营业状态为：{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }
}
