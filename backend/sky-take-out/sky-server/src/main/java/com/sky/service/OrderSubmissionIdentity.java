package com.sky.service;

import com.sky.dto.OrdersSubmitDTO;
import com.sky.exception.OrderBusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A digest of client-owned business inputs, independent of JSON field order or decimal formatting. */
public final class OrderSubmissionIdentity {
    private OrderSubmissionIdentity() {}
    public static String fingerprint(OrdersSubmitDTO input) {
        BigDecimal amount=input.getAmount();
        if(amount==null || amount.signum()<=0 || amount.compareTo(new BigDecimal("99999999.99"))>0
                || amount.stripTrailingZeros().scale()>2) throw new OrderBusinessException("请确认有效的订单金额后重新提交");
        if(input.getClientRequestId()==null) return null;
        if(!input.getClientRequestId().matches("[A-Za-z0-9_-]{1,64}")) throw new OrderBusinessException("提交请求标识无效");
        StringBuilder canonical=new StringBuilder();
        Object[] values={input.getAddressBookId(),input.getPayMethod(),input.getRemark(),input.getEstimatedDeliveryTime(),
                input.getDeliveryStatus(),input.getTablewareStatus(),input.getTablewareNumber(),amount.stripTrailingZeros().toPlainString()};
        for(Object value:values) {
            String text=value==null ? null : value.toString();
            canonical.append(text==null ? -1 : text.length()).append(':');
            if(text!=null) canonical.append(text);
        }
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(); for(byte part:digest) hex.append(String.format("%02x",part & 255));
            return hex.toString();
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
}
