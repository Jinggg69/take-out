package com.junzhou.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.entity.RefundRequest;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.exception.WechatApiException;
import com.junzhou.mapper.RefundRequestMapper;
import com.junzhou.utils.WeChatPayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Provider calls are outside database transactions; a stable number and lease make recovery repeatable. */
@Service
@Slf4j
public class RefundWorker {
    @Autowired private RefundRequestMapper refunds;
    @Autowired private RefundTransactionService transactions;
    @Autowired private WeChatPayUtil provider;

    public void processDue() {
        for(String number:refunds.due(LocalDateTime.now())) process(number,false);
    }

    public void retry(Long orderId) {
        if(orderId==null || orderId<=0) throw new OrderBusinessException("订单不存在");
        // Look up the immutable refund identity without extending a transaction across the network.
        RefundRequest refund=refunds.findByOrderId(orderId);
        if(refund==null) throw new OrderBusinessException("退款请求不存在");
        process(refund.getOutRefundNo(),true);
    }

    public void process(String number,boolean force) {
        RefundRequest claim=transactions.claim(number,force);
        if(claim==null) return;
        try {
            JSONObject result;
            if(!RefundRequest.REQUESTED.equals(claim.getStatus()) || claim.getAttempts()>1 || Boolean.TRUE.equals(claim.getQueryOnly())) {
                try { result=JSON.parseObject(provider.queryRefund(number)); }
                catch(WechatApiException ex) {
                    if(ex.getStatusCode()!=404 || !"RESOURCE_NOT_EXISTS".equals(ex.getErrorCode()) || Boolean.TRUE.equals(claim.getQueryOnly())) throw ex;
                    result=submit(claim);
                }
            } else result=submit(claim);
            transactions.recordResult(number,result);
        } catch(Exception ex) {
            String code=ex instanceof WechatApiException ? ((WechatApiException)ex).getErrorCode() : "RESULT_UNCONFIRMED";
            transactions.recordFailure(claim,code);
            log.warn("退款结果待核验，退款号={}，原因={}",number,code);
        }
    }

    private JSONObject submit(RefundRequest claim) throws Exception {
        BigDecimal amount=BigDecimal.valueOf(claim.getAmountFen(),2);
        return JSON.parseObject(provider.refund(claim.getOutTradeNo(),claim.getOutRefundNo(),amount,amount));
    }
}
