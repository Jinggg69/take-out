package com.sky.controller.notify;

import com.alibaba.fastjson.JSON;
import com.sky.exception.OrderBusinessException;
import com.sky.payment.VerifiedNotification;
import com.sky.service.PaymentLifecycleService;
import com.sky.service.RefundTransactionService;
import com.sky.service.WeChatNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {
    @Autowired private WeChatNotificationService notifications;
    @Autowired private PaymentLifecycleService payments;
    @Autowired private RefundTransactionService refunds;

    @PostMapping("/paySuccess")
    public void paySuccessNotify(HttpServletRequest request,HttpServletResponse response) throws Exception {
        handle(request,response,false);
    }
    @PostMapping("/refundSuccess")
    public void refundNotify(HttpServletRequest request,HttpServletResponse response) throws Exception {
        handle(request,response,true);
    }
    private void handle(HttpServletRequest request,HttpServletResponse response,boolean refund) throws Exception {
        try {
            ByteArrayOutputStream buffer=new ByteArrayOutputStream(); byte[] part=new byte[4096]; int count;
            while((count=request.getInputStream().read(part))!=-1) {
                if(buffer.size()+count>1024*1024) throw new IllegalArgumentException("Notification is too large");
                buffer.write(part,0,count);
            }
            VerifiedNotification notice=notifications.verify(new String(buffer.toByteArray(),StandardCharsets.UTF_8),
                    request.getHeader("Wechatpay-Timestamp"),request.getHeader("Wechatpay-Nonce"),
                    request.getHeader("Wechatpay-Serial"),request.getHeader("Wechatpay-Signature"));
            if(refund) {
                String event=notice.getEventType();
                if(!("REFUND.SUCCESS".equals(event)||"REFUND.CLOSED".equals(event)||"REFUND.ABNORMAL".equals(event))
                        || !event.substring(7).equals(notice.getResource().getString("refund_status"))) throw new IllegalArgumentException("Unexpected refund event");
                refunds.notifyResult(notice.getResource());
            } else {
                if(!"TRANSACTION.SUCCESS".equals(notice.getEventType())) throw new IllegalArgumentException("Unexpected payment event");
                payments.confirmPayment(notice.getResource());
            }
            response.setStatus(200); response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSON.toJSONString(Collections.singletonMap("code","SUCCESS")));
        } catch(SecurityException ex) { failure(response,401); }
        catch(IllegalStateException ex) { log.warn("支付通知配置未就绪"); failure(response,503); }
        catch(DataAccessException ex) { log.warn("支付通知存储暂不可用"); failure(response,503); }
        catch(OrderBusinessException | IllegalArgumentException ex) { failure(response,400); }
        catch(Exception ex) { log.warn("支付通知处理暂不可用"); failure(response,500); }
    }
    private void failure(HttpServletResponse response,int status) throws Exception {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"FAIL\",\"message\":\"通知未完成处理\"}");
    }
}
