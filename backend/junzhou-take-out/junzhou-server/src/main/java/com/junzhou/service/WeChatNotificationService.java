package com.junzhou.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.payment.VerifiedNotification;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.utils.WeChatPayUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Clock;
import java.util.Base64;

@Service
public class WeChatNotificationService {
    private final WeChatProperties properties;
    private final Clock clock;
    @Autowired
    public WeChatNotificationService(WeChatProperties properties) { this(properties, Clock.systemUTC()); }
    public WeChatNotificationService(WeChatProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }

    public VerifiedNotification verify(String rawBody, String timestamp, String nonce, String serial, String signature) {
        if (rawBody == null || rawBody.isEmpty() || blank(timestamp) || blank(nonce) || blank(serial) || blank(signature)) throw invalid();
        try {
            if (!timestamp.matches("[0-9]{1,19}")) throw invalid();
            long signedAt = Long.parseLong(timestamp), now = clock.instant().getEpochSecond();
            if (signedAt < now - 300 || signedAt > now + 60) throw invalid();
        } catch (NumberFormatException exception) { throw invalid(); }
        PublicKey key = WeChatPayUtil.resolvePlatformPublicKey(properties, serial, clock);
        byte[] message = (timestamp + "\n" + nonce + "\n" + rawBody + "\n").getBytes(StandardCharsets.UTF_8);
        if (!WeChatPayUtil.verifyRsa(key, message, signature)) throw invalid();
        byte[] apiKey = properties.getApiV3Key() == null ? null : properties.getApiV3Key().getBytes(StandardCharsets.UTF_8);
        if (apiKey == null || apiKey.length != 32) throw new IllegalStateException("WeChat Pay API v3 key must contain 32 bytes");
        try {
            JSONObject envelope = JSON.parseObject(rawBody);
            if (envelope == null) throw invalid();
            String eventId = requiredString(envelope, "id"), eventType = requiredString(envelope, "event_type");
            if (!"encrypt-resource".equals(requiredString(envelope, "resource_type"))) throw invalid();
            Object resourceObject = envelope.get("resource");
            if (!(resourceObject instanceof JSONObject)) throw invalid();
            JSONObject encrypted = (JSONObject)resourceObject;
            if (!"AEAD_AES_256_GCM".equals(requiredString(encrypted, "algorithm"))) throw invalid();
            String expectedType;
            if ("TRANSACTION.SUCCESS".equals(eventType)) expectedType = "transaction";
            else if ("REFUND.SUCCESS".equals(eventType) || "REFUND.ABNORMAL".equals(eventType) || "REFUND.CLOSED".equals(eventType)) expectedType = "refund";
            else throw invalid();
            if (!expectedType.equals(requiredString(encrypted, "original_type"))) throw invalid();
            String iv = requiredString(encrypted, "nonce");
            Object aadValue = encrypted.get("associated_data");
            if (aadValue != null && !(aadValue instanceof String)) throw invalid();
            String associatedData = aadValue == null ? "" : (String)aadValue;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(apiKey, "AES"), new GCMParameterSpec(128, iv.getBytes(StandardCharsets.UTF_8)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(requiredString(encrypted, "ciphertext")));
            JSONObject resource = JSON.parseObject(new String(decrypted, StandardCharsets.UTF_8));
            if (resource == null) throw invalid();
            return new VerifiedNotification(eventId, eventType, resource);
        } catch (Exception exception) { throw invalid(); }
    }
    private static String requiredString(JSONObject object, String name) {
        Object value = object.get(name); if (!(value instanceof String) || blank((String)value)) throw invalid(); return (String)value;
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static SecurityException invalid() { return new SecurityException("Invalid WeChat Pay notification"); }
}
