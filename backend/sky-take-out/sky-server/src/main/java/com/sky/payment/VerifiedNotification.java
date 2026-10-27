package com.sky.payment;

import com.alibaba.fastjson.JSONObject;

/** Authenticated envelope and decrypted resource; business validation remains with the consumer. */
public final class VerifiedNotification {
    private final String eventId;
    private final String eventType;
    private final JSONObject resource;
    public VerifiedNotification(String eventId, String eventType, JSONObject resource) {
        this.eventId = eventId; this.eventType = eventType; this.resource = resource;
    }
    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public JSONObject getResource() { return resource; }
}
