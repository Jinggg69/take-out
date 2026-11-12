package com.junzhou.dto;

import java.math.BigDecimal;
import lombok.Data;

/** Explicit public whitelist, deliberately independent of persistence entities. */
@Data
public class StoreContextDTO {
    private String merchantId;
    private String merchantName;
    private String logoUrl;
    private String storeId;
    private String storeName;
    private String address;
    private String contactPhone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String coordinateSystem = "BD09";
    private Boolean open;
    private String opensAt;
    private String closesAt;
    private String minimumOrderAmount;
    private String packFeePerItem;
    private String deliveryFee;
    private String freeDeliveryThreshold;
    private Integer deliveryRadiusMeters;
    private String configVersion;
}
