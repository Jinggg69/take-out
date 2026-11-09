package com.junzhou.payment;

import com.junzhou.dto.OrdersSubmitDTO;
import com.junzhou.exception.OrderBusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A digest of client-owned business inputs, independent of JSON field order or decimal formatting. */
public final class OrderSubmissionIdentity {
    private OrderSubmissionIdentity() {}
    public static String cartFingerprint(java.util.List<com.junzhou.entity.ShoppingCart> cart) {
        java.util.TreeMap<String, Long> lines = new java.util.TreeMap<>();
        for (com.junzhou.entity.ShoppingCart row : cart) {
            String flavor = row.getDishId()==null ? "" : (row.getDishFlavor()==null ? "" : row.getDishFlavor().trim());
            String key = (row.getDishId()==null ? "S:"+row.getSetmealId() : "D:"+row.getDishId())+":"+flavor.length()+":"+flavor;
            lines.merge(key, row.getNumber().longValue(), Long::sum);
        }
        return digest(lines.toString());
    }
    public static String fingerprint(OrdersSubmitDTO input, String cartFingerprint) {
        String base=fingerprint(input);
        if (base==null) return null;
        com.alibaba.fastjson.JSONArray context=new com.alibaba.fastjson.JSONArray();
        context.add(com.junzhou.context.RequestScope.requireTenantId());
        context.add(com.junzhou.context.RequestScope.requireStoreId());
        context.add(Fulfillment.type(input.getFulfillmentType()));
        context.add(input.getContactName()); context.add(input.getContactPhone()); context.add(input.getTableId());
        if (Fulfillment.DINE_IN.equals(Fulfillment.type(input.getFulfillmentType()))) {
            context.add(input.getDiningSessionId());context.add(input.getDiningToken());
        }
        context.add(input.getConfigVersion()); context.add(cartFingerprint);
        return digest(base+":"+context.toJSONString());
    }
    private static String digest(String text) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(); for(byte part:bytes) hex.append(String.format("%02x",part & 255));
            return hex.toString();
        } catch(java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
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
