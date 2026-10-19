package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端订单接口
 */
@RestController("userOrderController")
@RequestMapping("/user/order")
@Api(tags = "用户端订单接口")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     * 分页查询当前用户的历史订单
     * @param page 页码
     * @param pageSize 每页记录数
     * @param status 订单状态
     * @return 历史订单分页结果
     */
    @GetMapping("/historyOrders")
    @ApiOperation("分页查询当前用户历史订单")
    public Result<PageResult> historyOrders(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) Integer status) {
        log.info("历史订单分页查询：page={}, pageSize={}, status={}", page, pageSize, status);
        PageResult pageResult = orderService.historyOrders(page, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 查询当前用户的订单详情
     * @param id 订单id
     * @return 订单和订单明细
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询当前用户订单详情")
    public Result<OrderVO> orderDetail(@PathVariable Long id) {
        log.info("查询当前用户订单详情：{}", id);
        OrderVO orderVO = orderService.userOrderDetail(id);
        return Result.success(orderVO);
    }

    /**
     * 用户取消订单
     * @param id 订单id
     * @return 操作结果
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("用户取消订单")
    public Result<Void> cancel(@PathVariable Long id) {
        log.info("用户取消订单：{}", id);
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单，将历史订单商品加入购物车
     * @param id 订单id
     * @return 操作结果
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单，将历史订单商品加入购物车")
    public Result<Void> repetition(@PathVariable Long id) {
        log.info("再来一单：{}", id);
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 用户下单
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户端订单提交：{}", ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO = orderService.orderSubmit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付，生成微信预支付交易单
     * @param ordersPaymentDTO 订单支付信息
     * @return 小程序调起支付所需的参数
     * @throws Exception 微信支付调用异常
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 用户催单接口占位，暂未调用催单业务
     * @param id 订单id
     * @return 操作结果
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("用户催单")
    public Result reminder(@PathVariable("id") Long id){

        return Result.success();

    }
}
