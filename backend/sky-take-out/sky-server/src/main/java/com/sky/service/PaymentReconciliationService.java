package com.sky.service;

import com.alibaba.fastjson.JSONObject;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.utils.WeChatPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/** Recovers cancelled payments even when their notification was lost. */
@Service
@Slf4j
public class PaymentReconciliationService {
    @Autowired private OrderMapper orders;
    @Autowired private WeChatPayUtil provider;
    @Autowired private PaymentLifecycleService lifecycle;

    public void reconcileCancelled() {
        for(Orders order:orders.cancelledPaymentsToCheck(LocalDateTime.now().minusMinutes(5))) {
            orders.recordPaymentCheck(order.getId(),LocalDateTime.now());
            try {
                JSONObject result=provider.queryOrder(order.getNumber());
                if(result==null || !order.getNumber().equals(result.getString("out_trade_no"))) continue;
                String state=result.getString("trade_state");
                if("SUCCESS".equals(state)) lifecycle.confirmPayment(result);
                else if("NOTPAY".equals(state) || "USERPAYING".equals(state)) {
                    provider.closeOrder(order.getNumber());
                    orders.markClosedPayment(order.getId(),"CLOSED",LocalDateTime.now());
                } else if("CLOSED".equals(state)||"REVOKED".equals(state)||"PAYERROR".equals(state)) {
                    orders.markClosedPayment(order.getId(),state,LocalDateTime.now());
                }
            } catch(Exception ex) { log.warn("已取消订单支付结果待核验，orderId={}",order.getId()); }
        }
    }
}
