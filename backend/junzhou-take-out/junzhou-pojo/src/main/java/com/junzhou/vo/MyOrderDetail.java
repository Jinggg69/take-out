package com.junzhou.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MyOrderDetail extends MyOrderSummary {
    private List<DetailItem> orderDetails;
    private String externalPayAmount;
    private java.util.Map<String,Object> membership;
    private String goodsAmount, packFee, deliveryFee;
    private String contactName, contactPhone, deliveryAddress, remark, pickupNumber, tableName;
    private String cancelReason, rejectionReason, estimatedDeliveryTime;

    @Data public static class DetailItem {
        private String name, image;
        private Integer number;
        private String unitPrice, dishFlavor;
    }
}
