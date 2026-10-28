package com.junzhou.payment;

import com.junzhou.exception.OrderBusinessException;
import java.math.BigDecimal;

/** 微信支付金额校验工具。 */
public final class PaymentAmounts {
    private PaymentAmounts() {}

    /** 将金额校验为整数分。 */
    public static long fen(Object value) {
        if(!(value instanceof Number)) throw new OrderBusinessException("金额必须是整数分");
        try { return new BigDecimal(value.toString()).longValueExact(); }
        catch(RuntimeException ex) { throw new OrderBusinessException("金额必须是整数分"); }
    }
}
