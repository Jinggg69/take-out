package com.junzhou.controller.admin;

import com.junzhou.dto.OrdersCancelDTO;
import com.junzhou.dto.OrdersConfirmDTO;
import com.junzhou.dto.OrdersPageQueryDTO;
import com.junzhou.dto.OrdersRejectionDTO;
import com.junzhou.result.PageResult;
import com.junzhou.result.Result;
import com.junzhou.service.OrderService;
import com.junzhou.service.PaymentRecoveryService;
import com.junzhou.vo.OrderStatisticsVO;
import com.junzhou.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端订单管理接口
 */
@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Api(tags = "商家端订单管理接口")
@Slf4j
public class OrderController {
    @Autowired
    private OrderService orderService;
    @Autowired private PaymentRecoveryService refundWorker;

    @PostMapping("/{id}/refund/retry")
    @ApiOperation("按原退款单号核验或重试已有退款")
    public Result<Void> retryRefund(@PathVariable Long id) {
        refundWorker.retry(id);
        return Result.success();
    }

    /**
     * 订单条件分页查询
     * @param ordersPageQueryDTO 订单查询条件
     * @return 订单分页结果
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("订单搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("订单条件分页查询：{}", ordersPageQueryDTO);
        PageResult pageResult = orderService.conditionSearch(ordersPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 统计各状态的订单数量
     * @return 待接单、待派送和派送中的订单数量
     */
    @GetMapping("/statistics")
    @ApiOperation("统计待接单、待派送、派送中的订单数量")
    public Result<OrderStatisticsVO> statistics() {
        log.info("统计各状态订单数量");
        OrderStatisticsVO orderStatisticsVO = orderService.statistics();
        return Result.success(orderStatisticsVO);
    }

    /**
     * 查询订单详情
     * @param id 订单id
     * @return 订单和订单明细
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        log.info("查询订单详情：{}", id);
        OrderVO orderVO = orderService.details(id);
        return Result.success(orderVO);
    }

    /**
     * 接单
     * @param ordersConfirmDTO 接单信息
     * @return 操作结果
     */
    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result<Void> confirm(@RequestBody OrdersConfirmDTO ordersConfirmDTO) {
        log.info("接单：{}", ordersConfirmDTO);
        orderService.confirm(ordersConfirmDTO);
        return Result.success();
    }

    /**
     * 拒单
     * @param ordersRejectionDTO 拒单信息
     * @return 操作结果
     */
    @PutMapping("/rejection")
    @ApiOperation("拒单")
    public Result<Void> rejection(@RequestBody OrdersRejectionDTO ordersRejectionDTO) {
        log.info("拒单：{}", ordersRejectionDTO);
        orderService.rejection(ordersRejectionDTO);
        return Result.success();
    }

    /**
     * 商家取消订单
     * @param ordersCancelDTO 订单取消信息
     * @return 操作结果
     */
    @PutMapping("/cancel")
    @ApiOperation("商家取消订单")
    public Result<Void> cancel(@RequestBody OrdersCancelDTO ordersCancelDTO) {
        log.info("商家取消订单：{}", ordersCancelDTO);
        orderService.cancel(ordersCancelDTO);
        return Result.success();
    }

    /**
     * 派送订单
     * @param id 订单id
     * @return 操作结果
     */
    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result<Void> delivery(@PathVariable Long id) {
        log.info("派送订单：{}", id);
        orderService.delivery(id);
        return Result.success();
    }

    /**
     * 完成订单
     * @param id 订单id
     * @return 操作结果
     */
    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result<Void> complete(@PathVariable Long id) {
        log.info("完成订单：{}", id);
        orderService.complete(id);
        return Result.success();
    }
}
