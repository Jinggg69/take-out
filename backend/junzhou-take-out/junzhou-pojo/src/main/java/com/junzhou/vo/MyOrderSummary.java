package com.junzhou.vo;

import lombok.Data;
import java.util.List;

/** Explicit customer-visible fields; never inherit the persistence order entity. */
@Data
public class MyOrderSummary {
    private String orderId, orderNumber, merchantId, merchantName, storeId, storeName;
    private boolean storeAvailable;
    private String orderTime;
    private Integer status, payStatus;
    private String refundStatus, fulfillmentType, fulfillmentStatus, amount;
    private int totalQuantity;
    private List<Item> items;
    private String clientRequestId;

    @Data public static class Item {
        private String name, image;
        private Integer number;
    }
}
