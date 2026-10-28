package com.junzhou.test.support;

import com.alibaba.fastjson.JSONObject;
import com.junzhou.properties.WeChatProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import javax.security.auth.x500.X500Principal;

/** Synthetic keys and real RSA/AES only. No network or merchant credentials. */
public final class SignedWechatNotifications {
    public static final String API_KEY = "0123456789abcdef0123456789abcdef";
    public static final String KEY_ID = "PUB_KEY_ID_TEST_ONLY";
    private final KeyPair keyPair;
    private final Clock clock;
    private String signingSerial = KEY_ID;

    public SignedWechatNotifications(WeChatProperties properties, Path directory) throws Exception {
        this(properties, directory, Clock.systemUTC());
    }

    public SignedWechatNotifications(WeChatProperties properties, Path directory, Clock clock) throws Exception {
        this.clock = clock;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        Path keyFile = Files.createTempFile(directory, "wechat-test-public-", ".pem");
        Files.write(keyFile, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()).getBytes(StandardCharsets.US_ASCII));
        properties.setPlatformPublicKeyId(KEY_ID);
        properties.setPlatformPublicKeyFilePath(keyFile.toString());
        properties.setApiV3Key(API_KEY);
    }

    public Signed wrap(JSONObject resource, String eventType) throws Exception {
        String iv = "test-nonce12";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(API_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, iv.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD("test-data".getBytes(StandardCharsets.UTF_8));
        JSONObject encrypted = new JSONObject();
        encrypted.put("algorithm", "AEAD_AES_256_GCM");
        encrypted.put("original_type", eventType.startsWith("REFUND.") ? "refund" : "transaction");
        encrypted.put("nonce", iv);
        encrypted.put("associated_data", "test-data");
        encrypted.put("ciphertext", Base64.getEncoder().encodeToString(cipher.doFinal(resource.toJSONString().getBytes(StandardCharsets.UTF_8))));
        JSONObject envelope = new JSONObject();
        envelope.put("id", "EV-" + UUID.randomUUID());
        envelope.put("event_type", eventType);
        envelope.put("resource_type", "encrypt-resource");
        envelope.put("resource", encrypted);
        return sign(envelope.toJSONString());
    }

    public Signed sign(String rawBody) throws Exception {
        return sign(rawBody, Long.toString(clock.instant().getEpochSecond()));
    }

    public Signed sign(String rawBody, String timestamp) throws Exception {
        String nonce = "signed-test-nonce";
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update((timestamp + "\n" + nonce + "\n" + rawBody + "\n").getBytes(StandardCharsets.UTF_8));
        return new Signed(rawBody, timestamp, nonce, signingSerial, Base64.getEncoder().encodeToString(signature.sign()));
    }

    /** Minimal synthetic X.509 fixture, signed with this fixture's generated RSA key. */
    public Path useCertificate(WeChatProperties properties, Path directory, String serialHex, boolean retainPublicKey) throws Exception {
        byte[] algorithm = new byte[]{0x30,0x0d,0x06,0x09,0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x0b,0x05,0x00};
        byte[] name = new X500Principal("CN=Synthetic WeChat Test Only").getEncoded();
        byte[] validity = der(0x30, concat(der(0x17, "200101000000Z".getBytes(StandardCharsets.US_ASCII)),
                der(0x17, "350101000000Z".getBytes(StandardCharsets.US_ASCII))));
        byte[] tbs = der(0x30, concat(der(0x02, new BigInteger(serialHex,16).toByteArray()), algorithm, name, validity, name, keyPair.getPublic().getEncoded()));
        Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(keyPair.getPrivate()); signature.update(tbs);
        byte[] certificate = der(0x30, concat(tbs, algorithm, der(0x03, concat(new byte[]{0}, signature.sign()))));
        Path file = Files.createTempFile(directory, "wechat-test-certificate-", ".pem");
        Files.write(file, pem("CERTIFICATE", certificate).getBytes(StandardCharsets.US_ASCII));
        properties.setWeChatPayCertFilePath(file.toString());
        if (!retainPublicKey) { properties.setPlatformPublicKeyId(null); properties.setPlatformPublicKeyFilePath(null); }
        signingSerial = serialHex.toUpperCase(java.util.Locale.ROOT);
        return file;
    }

    private static byte[] concat(byte[]... arrays) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for(byte[] array:arrays) output.write(array); return output.toByteArray();
    }
    private static byte[] der(int tag, byte[] value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); output.write(tag);
        if(value.length<128) output.write(value.length);
        else if(value.length<256) { output.write(0x81); output.write(value.length); }
        else { output.write(0x82); output.write(value.length>>8); output.write(value.length); }
        output.write(value); return output.toByteArray();
    }

    public static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----\n";
    }

    public static final class Signed {
        public final String body, timestamp, nonce, serial, signature;
        public Signed(String body, String timestamp, String nonce, String serial, String signature) {
            this.body = body; this.timestamp = timestamp; this.nonce = nonce; this.serial = serial; this.signature = signature;
        }
    }
}
