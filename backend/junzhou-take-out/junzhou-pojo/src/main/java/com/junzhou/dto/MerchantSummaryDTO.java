package com.junzhou.dto;

import lombok.Data;

/** Public merchant directory projection; never contains tenant configuration. */
@Data
public class MerchantSummaryDTO {
    private String merchantId;
    private String merchantName;
    private String logoUrl;
    private String introduction;
    private Integer activeStoreCount;
}
