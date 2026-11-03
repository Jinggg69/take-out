package com.junzhou.payment;

import com.alibaba.fastjson.JSONObject;

/** Authenticated envelope and decrypted resource; business validation remains with the consumer. */
public final class VerifiedNotification {
    private final String paymentAccountId;
    public String getPaymentAccountId() { return paymentAccountId; }
    public VerifiedNotification forAccount(String id) { return new VerifiedNotification(eventId,eventType,resource,id); }
    private final String eventId;
    private final String eventType;
    private final JSONObject resource;
    public VerifiedNotification(String eventId, String eventType, JSONObject resource) {
        this(eventId,eventType,resource,"legacy");
    }
    private VerifiedNotification(String eventId,String eventType,JSONObject resource,String accountId) {
        this.paymentAccountId=accountId;
        this.eventId = eventId; this.eventType = eventType; this.resource = resource;
    }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public JSONObject getResource() { return resource; }
}
