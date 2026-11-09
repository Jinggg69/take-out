package com.junzhou.entity;

import lombok.Data;
import java.math.BigDecimal;

/** Daily business hours use Asia/Shanghai; coordinates use Baidu BD-09. */
@Data
public class StoreSettings {
    private Long id;
    private String name;
    private Long version;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String opensAt;
    private String closesAt;
    private Integer deliveryRadiusMeters;
    private BigDecimal minimumOrderAmount;
    private BigDecimal packFeePerItem;
    private BigDecimal deliveryFee;
    private BigDecimal freeDeliveryThreshold;
    private Boolean open;
}
