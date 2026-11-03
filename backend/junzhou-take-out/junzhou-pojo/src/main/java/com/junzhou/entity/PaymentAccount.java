package com.junzhou.entity;

import lombok.Data;

/** Immutable merchant identity; credentials are held outside the database. */
@Data
public class PaymentAccount {
    private String id;
    private Long tenantId;
    private Long storeId;
    private String mchid;
    private String appid;
}
