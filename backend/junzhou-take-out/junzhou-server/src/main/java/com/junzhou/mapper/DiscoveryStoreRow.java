package com.junzhou.mapper;

import com.junzhou.entity.StoreSettings;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Internal mapper projection. Must never be returned by a public controller. */
@Data
@EqualsAndHashCode(callSuper = true)
public class DiscoveryStoreRow extends StoreSettings {
    private Long merchantId;
    private String merchantName;
    private String logoUrl;
}
