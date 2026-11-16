package com.junzhou.vo;

import lombok.Data;
import java.util.List;

@Data
public class MyOrderFilters {
    private String merchantId, merchantName;
    private List<StoreOption> stores;
    @Data public static class StoreOption {
        private String storeId, storeName;
    }
}
