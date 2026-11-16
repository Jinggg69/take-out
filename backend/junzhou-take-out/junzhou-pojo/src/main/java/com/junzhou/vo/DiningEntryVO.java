package com.junzhou.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.junzhou.entity.DiningTable;
import lombok.Data;

/** Safe table entry DTO retaining the existing numeric id/name contract. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiningEntryVO {
    private Long id;
    private String name;
    private Long tenantId;
    private String merchantId;
    private String merchantName;
    private Long storeId;
    private String storeName;
    private String currentSessionId;
    public static DiningEntryVO from(DiningTable table,boolean joined){
        DiningEntryVO vo=new DiningEntryVO(); vo.setId(table.getId()); vo.setName(table.getName());
        vo.setTenantId(table.getTenantId()); vo.setMerchantId(table.getTenantId().toString());
        vo.setMerchantName(table.getMerchantName()); vo.setStoreId(table.getStoreId()); vo.setStoreName(table.getStoreName());
        if(joined) vo.setCurrentSessionId(table.getCurrentSessionId());
        return vo;
    }
}
