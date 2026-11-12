package com.junzhou.controller.user;

import com.junzhou.annotation.MiniappEntry;
import com.junzhou.result.PageResult;
import com.junzhou.result.Result;
import com.junzhou.service.MyOrdersService;
import com.junzhou.vo.MyOrderDetail;
import com.junzhou.vo.MyOrderFilters;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/me")
@MiniappEntry(MiniappEntry.Scope.IDENTITY)
@Api(tags="当前商家本人订单（仅用户身份，无需门店）")
public class MyOrdersController {
    private final MyOrdersService service;
    public MyOrdersController(MyOrdersService service){this.service=service;}
    @GetMapping("/orders")
    @ApiOperation("分页读取本人在指定商家的订单，可筛选该商家的门店")
    public Result<PageResult> list(@RequestParam Long merchantId,@RequestParam(required=false) Long storeId,
        @RequestParam(required=false) Integer status,@RequestParam(required=false) Boolean refundOnly,
        @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
        return Result.success(service.list(merchantId,storeId,status,refundOnly,page,pageSize));
    }
    @GetMapping("/orders/{orderId}")
    @ApiOperation("只读本人订单详情，不含取餐凭据")
    public Result<MyOrderDetail> detail(@PathVariable Long orderId,@RequestParam Long merchantId){return Result.success(service.detail(merchantId,orderId));}
    @GetMapping("/order-filters")
    @ApiOperation("读取当前商家本人历史订单门店筛选项")
    public Result<MyOrderFilters> filters(@RequestParam Long merchantId){return Result.success(service.filters(merchantId));}
}
