package com.junzhou.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.payment.VerifiedNotification;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.service.WeChatNotificationService;
import com.junzhou.test.support.SignedWechatNotifications;
import com.junzhou.test.support.SignedWechatNotifications.Signed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class WeChatNotificationServiceTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC);
    private WeChatProperties properties;
    private SignedWechatNotifications signer;
    private WeChatNotificationService service;

    @BeforeEach void setup() throws Exception {
        properties = new WeChatProperties();
        signer = new SignedWechatNotifications(properties, directory, clock);
        service = new com.junzhou.service.impl.WeChatNotificationServiceImpl(properties, clock);
    }

    @ParameterizedTest @ValueSource(strings={"TRANSACTION.SUCCESS", "REFUND.SUCCESS", "REFUND.ABNORMAL", "REFUND.CLOSED"})
    void verifiesAndDecryptsAuthenticPaymentAndRefunds(String event) throws Exception {
        Signed signed = signer.wrap(JSON.parseObject("{\"out_trade_no\":\"ORDER-1\",\"description\":\"中文\"}"), event);
        VerifiedNotification verified = verify(signed);
        assertEquals(event, verified.getEventType());
        assertEquals(JSON.parseObject(signed.body).getString("id"), verified.getEventId());
        assertEquals("ORDER-1", verified.getResource().getString("out_trade_no"));
        assertEquals("中文", verified.getResource().getString("description"));
    }

    @Test void preservesExactNewlinesAndUtf8InSignedBody() throws Exception {
        Signed initial = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        Signed signed = signer.sign("\r\n" + initial.body + "\n");
        assertNotNull(verify(signed));
        assertThrows(SecurityException.class, () -> service.verify(signed.body.trim(), signed.timestamp, signed.nonce, signed.serial, signed.signature));
    }

    @ParameterizedTest @ValueSource(longs={-301, 61, Long.MIN_VALUE, Long.MAX_VALUE})
    void rejectsExpiredFutureAndOverflowingTimestamps(long delta) throws Exception {
        Signed original = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        String timestamp = delta == Long.MIN_VALUE || delta == Long.MAX_VALUE ? Long.toString(delta) : Long.toString(clock.instant().getEpochSecond()+delta);
        Signed signed = signer.sign(original.body, timestamp);
        assertThrows(SecurityException.class, () -> verify(signed));
    }

    @Test void rejectsUnknownSerialAndForgedSignature() throws Exception {
        Signed signed = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        assertThrows(SecurityException.class, () -> service.verify(signed.body, signed.timestamp, signed.nonce, "UNKNOWN", signed.signature));
        assertThrows(SecurityException.class, () -> service.verify(signed.body, signed.timestamp, signed.nonce, signed.serial, "Zm9yZ2Vk"));
        assertThrows(SecurityException.class, () -> service.verify(signed.body, signed.timestamp, "", signed.serial, signed.signature));
    }

    @Test void verifiesLocallyPinnedCertificateAndTwoCertificateRotationBundle() throws Exception {
        Path first = signer.useCertificate(properties, directory, "A123", false);
        Signed firstSigned = signer.wrap(JSON.parseObject("{\"out_trade_no\":\"CERT-1\"}"), "TRANSACTION.SUCCESS");
        assertEquals("CERT-1", verify(firstSigned).getResource().getString("out_trade_no"));
        WeChatProperties secondProperties = new WeChatProperties();
        SignedWechatNotifications second = new SignedWechatNotifications(secondProperties, directory, clock);
        Path secondCertificate = second.useCertificate(secondProperties, directory, "B456", false);
        Files.write(first, Files.readAllBytes(secondCertificate), StandardOpenOption.APPEND);
        Signed secondSigned = second.wrap(JSON.parseObject("{\"out_trade_no\":\"CERT-2\"}"), "TRANSACTION.SUCCESS");
        assertEquals("CERT-1", verify(firstSigned).getResource().getString("out_trade_no"));
        assertEquals("CERT-2", verify(secondSigned).getResource().getString("out_trade_no"));
    }

    @Test void supportsPublicKeyAndCertificateDuringMigrationAndRejectsExpiredCertificate() throws Exception {
        Signed publicSigned = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        signer.useCertificate(properties, directory, "A123", true);
        Signed certificateSigned = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        assertNotNull(verify(publicSigned)); assertNotNull(verify(certificateSigned));
        Clock expiredClock = Clock.fixed(Instant.parse("2036-01-01T00:00:00Z"), ZoneOffset.UTC);
        Signed expired = signer.sign(certificateSigned.body, Long.toString(expiredClock.instant().getEpochSecond()));
        assertThrows(SecurityException.class, () -> new com.junzhou.service.impl.WeChatNotificationServiceImpl(properties, expiredClock)
                .verify(expired.body, expired.timestamp, expired.nonce, "A123", expired.signature));
    }

    @ParameterizedTest @ValueSource(strings={"id", "event_type", "resource_type", "algorithm", "original_type", "ciphertext", "nonce"})
    void rejectsMalformedEnvelopeEvenWithAuthenticSignature(String field) throws Exception {
        Signed initial = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        JSONObject envelope = JSON.parseObject(initial.body);
        if (envelope.containsKey(field)) envelope.put(field, ""); else envelope.getJSONObject("resource").put(field, "");
        Signed signed = signer.sign(envelope.toJSONString());
        assertThrows(SecurityException.class, () -> verify(signed));
    }

    @Test void rejectsWrongOriginalTypeAndUnauthenticCiphertext() throws Exception {
        Signed initial = signer.wrap(new JSONObject(), "REFUND.SUCCESS");
        JSONObject envelope = JSON.parseObject(initial.body);
        envelope.getJSONObject("resource").put("original_type", "transaction");
        assertThrows(SecurityException.class, () -> verify(signer.sign(envelope.toJSONString())));
        envelope.getJSONObject("resource").put("original_type", "refund");
        envelope.getJSONObject("resource").put("associated_data", "tampered");
        assertThrows(SecurityException.class, () -> verify(signer.sign(envelope.toJSONString())));
    }

    @Test void failsClosedWithoutTrustConfigurationOrValidApiKey() throws Exception {
        Signed signed = signer.wrap(new JSONObject(), "TRANSACTION.SUCCESS");
        properties.setApiV3Key("short");
        assertThrows(IllegalStateException.class, () -> verify(signed));
        properties.setApiV3Key(SignedWechatNotifications.API_KEY);
        properties.setPlatformPublicKeyFilePath(null);
        assertThrows(IllegalStateException.class, () -> verify(signed));
    }

    private VerifiedNotification verify(Signed s) {
        return service.verify(s.body, s.timestamp, s.nonce, s.serial, s.signature);
    }
}
