package com.junzhou.controller.user;

import com.junzhou.context.RequestScope;
import com.junzhou.payment.MockPaymentGateway;
import com.junzhou.service.OrderService;
import com.junzhou.service.PaymentLifecycleService;
import com.junzhou.result.Result;
import com.junzhou.vo.OrderVO;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user/order")
public class MockPaymentController {
    private final MockPaymentGateway gateway;
    private final PaymentLifecycleService lifecycle;
    private final OrderService orders;
    public MockPaymentController(MockPaymentGateway gateway,PaymentLifecycleService lifecycle,OrderService orders) {
        this.gateway=gateway; this.lifecycle=lifecycle; this.orders=orders;
    }
    @Data public static class Choice { private String outcome; }
    @PostMapping("/{id}/payment/mock")
    public Result<OrderVO> choose(@PathVariable Long id,@RequestBody Choice choice) {
        RequestScope.requireUserId();
        gateway.choose(id,choice.getOutcome());
        if("SUCCESS".equals(choice.getOutcome())) lifecycle.confirmPayment(gateway.query(id));
        return Result.success(orders.userOrderDetail(id));
    }
}
