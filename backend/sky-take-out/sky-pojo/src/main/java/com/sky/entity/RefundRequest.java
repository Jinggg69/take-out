package com.sky.entity;

import lombok.Data;
import java.time.LocalDateTime;

/** Durable full-refund intent. It never stores payment credentials or provider raw payloads. */
@Data
public class RefundRequest {
    public static final String REQUESTED="REQUESTED", PROCESSING="PROCESSING", VERIFYING="VERIFYING";
    public static final String SUCCESS="SUCCESS", CLOSED="CLOSED", ABNORMAL="ABNORMAL";
    private String outRefundNo;
    private Long orderId;
    private String outTradeNo;
    private String transactionId;
    private Long amountFen;
    private String status;
    private Boolean queryOnly;
    private String providerRefundId;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    private String leaseToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String lastError;
}
