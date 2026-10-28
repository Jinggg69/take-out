package com.junzhou.controller.user;


import com.junzhou.dto.ShoppingCartDTO;
import com.junzhou.entity.ShoppingCart;
import com.junzhou.result.Result;
import com.junzhou.service.ShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/shoppingCart")
@Slf4j
@Api(tags = "C端购物车相关接口")
public class ShoppingCartController {
    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO 购物车数据传输对象
     * @return Result 返回结果
     */
    @PostMapping("/add")
    @ApiOperation("添加购物车")
    public Result add(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("添加购物车,商品信息: {}", shoppingCartDTO);
        shoppingCartService.add(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 查询购物车列表
     *
     * @return Result 返回结果
     */
    @GetMapping("/list")
    @ApiOperation("查询购物车列表")
    public Result<List<ShoppingCart>> list() {
        List<ShoppingCart> shoppingCarts = shoppingCartService.showShoppingCart();
        return Result.success(shoppingCarts);
    }

    /**
     * 清空购物车
     *
     * @return Result 返回结果
     */
    @DeleteMapping("/clean")
    @ApiOperation("清空购物车")
    public Result clean() {
        shoppingCartService.clean();
        return Result.success();
    }

    /**
     * 删减购物车中商品的数量
     *
     * @param shoppingCartDTO 购物车数据传输对象
     * @return Result 返回结果
     */
    @PostMapping("/sub")
    @ApiOperation("删减购物车中商品的数量")
    public Result sub(@RequestBody ShoppingCartDTO shoppingCartDTO) {
        log.info("删减购物车中商品的数量,商品信息: {}", shoppingCartDTO);
        shoppingCartService.sub(shoppingCartDTO);
        return Result.success();
    }
}
