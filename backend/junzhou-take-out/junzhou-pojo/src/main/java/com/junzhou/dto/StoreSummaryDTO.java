package com.junzhou.dto;

import lombok.Data;

@Data
public class StoreSummaryDTO {
    private String storeId;
    private String merchantId;
    private String storeName;
    private String address;
    private Boolean open;
    private String opensAt;
    private String closesAt;
}
