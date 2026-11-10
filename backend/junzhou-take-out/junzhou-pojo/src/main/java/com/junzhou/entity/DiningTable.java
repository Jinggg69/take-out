package com.junzhou.entity;

import lombok.Data;

/** 门店桌台及当前桌次。桌码仅通过授权的专用接口返回。 */
@Data
public class DiningTable {
    private Long id;
    private Long tenantId;
    private Long storeId;
    private String name;
    private Integer enabled;
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String qrToken;
    private String currentSessionId;
    private String storeName;
}
