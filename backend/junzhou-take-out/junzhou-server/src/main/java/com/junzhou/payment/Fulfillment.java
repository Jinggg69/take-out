package com.junzhou.payment;

import com.junzhou.exception.OrderBusinessException;

/** Stable string codes, independent of legacy status and pay_status. */
public final class Fulfillment {
    public static final String DELIVERY = "DELIVERY", PICKUP = "PICKUP", DINE_IN = "DINE_IN";
    private Fulfillment() {}
    public static String type(String value) {
        if (value == null) return DELIVERY;
        if (!DELIVERY.equals(value) && !PICKUP.equals(value) && !DINE_IN.equals(value))
            throw new OrderBusinessException("履约类型无效");
        return value;
    }
}
