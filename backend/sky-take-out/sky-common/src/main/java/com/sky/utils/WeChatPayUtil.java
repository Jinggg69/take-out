package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.WechatApiException;
import com.sky.properties.WeChatProperties;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.Signer;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.*;

/** API v3 requests use the existing official Apache SDK for signing and response validation. */
@Component
public class WeChatPayUtil {
    public static final String JSAPI = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";
    public static final String REFUNDS = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";
    private static final String ORDER_PATH = "https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final WeChatProperties weChatProperties;

    /** Retains source compatibility with existing payment test doubles. Spring uses the injected constructor. */
    public WeChatPayUtil() { this(new WeChatProperties()); }
    @Autowired
    public WeChatPayUtil(WeChatProperties weChatProperties) { this.weChatProperties = weChatProperties; }

    protected CloseableHttpClient getClient() {
        final PlatformTrust trust = loadPlatformTrust(weChatProperties, Clock.systemUTC());
        Verifier verifier = new Verifier() {
            @Override public boolean verify(String serial, byte[] message, String signature) {
                PublicKey key = trust.keys.get(normalizeSerial(serial));
                if (key == null || !verifyRsa(key, message, signature)) {
                    // SDK 0.4.8 logs response bodies when verify returns false. Throw safely instead.
                    throw new SecurityException("WeChat Pay response signature is invalid");
                }
                return true;
            }
            @Override public X509Certificate getValidCertificate() { return trust.certificate; }
        };
        final String merchantId = requiredConfig(weChatProperties.getMchid());
        final PrivateKeySigner merchantSigner = new PrivateKeySigner(requiredConfig(weChatProperties.getMchSerialNo()), merchantPrivateKey());
        final WechatPay2Validator validator = new WechatPay2Validator(verifier);
        // The SDK's 0.4.8 execution layer logs Authorization and request bodies on HTTP errors,
        // and its credentials helper logs canonical bodies at DEBUG. Use its signer/validator
        // directly in an Apache execution layer that never logs payloads.
        HttpClientBuilder builder = new HttpClientBuilder() {
            @Override protected ClientExecChain decorateProtocolExec(final ClientExecChain protocol) {
                return (route, request, context, executionAware) -> {
                    String body = "";
                    if (request instanceof HttpEntityEnclosingRequest) {
                        HttpEntityEnclosingRequest enclosing = (HttpEntityEnclosingRequest)request;
                        if (enclosing.getEntity() != null) {
                            enclosing.setEntity(new BufferedHttpEntity(enclosing.getEntity()));
                            body = EntityUtils.toString(enclosing.getEntity(), StandardCharsets.UTF_8);
                        }
                    }
                    String timestamp = Long.toString(System.currentTimeMillis() / 1000);
                    String nonce = randomNonce();
                    String path = request.getURI().getRawPath();
                    if (request.getURI().getRawQuery() != null) path += "?" + request.getURI().getRawQuery();
                    String canonical = request.getRequestLine().getMethod() + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
                    Signer.SignatureResult signed = merchantSigner.sign(canonical.getBytes(StandardCharsets.UTF_8));
                    request.setHeader(HttpHeaders.AUTHORIZATION, "WECHATPAY2-SHA256-RSA2048 mchid=\"" + merchantId
                            + "\",nonce_str=\"" + nonce + "\",timestamp=\"" + timestamp + "\",serial_no=\""
                            + signed.getCertificateSerialNumber() + "\",signature=\"" + signed.getSign() + "\"");
                    CloseableHttpResponse response = protocol.execute(route, request, context, executionAware);
                    try {
                        int status = response.getStatusLine().getStatusCode();
                        if (status >= 200 && status < 300) {
                            if (response.getEntity() != null) response.setEntity(new BufferedHttpEntity(response.getEntity()));
                            if (!validator.validate(response)) throw new SecurityException("Invalid WeChat Pay response");
                        }
                        return response;
                    } catch (Exception exception) {
                        response.close();
                        throw new java.io.IOException("WeChat Pay response verification failed");
                    }
                };
            }
        };
        builder.setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000).setSocketTimeout(10000).build());
        builder.disableAutomaticRetries();
        builder.disableRedirectHandling();
        return builder.build();
    }

    private PrivateKey merchantPrivateKey() {
        try (InputStream input = Files.newInputStream(Paths.get(requiredConfig(weChatProperties.getPrivateKeyFilePath())))) {
            return PemUtil.loadPrivateKey(input);
        } catch (Exception exception) {
            throw new IllegalStateException("WeChat Pay merchant key is not configured correctly");
        }
    }

    private String execute(HttpRequestBase request) {
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        if (!blank(weChatProperties.getPlatformPublicKeyId())) request.setHeader("Wechatpay-Serial", weChatProperties.getPlatformPublicKeyId());
        try (CloseableHttpClient client = getClient(); CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status < 200 || status >= 300) {
                String code = null;
                try { JSONObject error = JSON.parseObject(body); if (error != null) code = error.getString("code"); }
                catch (RuntimeException ignored) { }
                throw new WechatApiException(status, code);
            }
            return body;
        } catch (WechatApiException | IllegalStateException exception) { throw exception; }
        catch (Exception exception) {
            // Some SDK exceptions contain signed bodies. Do not retain their messages or causes.
            throw new WechatApiException(0, "TRANSPORT_OR_VERIFICATION_ERROR");
        }
    }

    private String post(String url, JSONObject body) {
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(body.toJSONString(), ContentType.APPLICATION_JSON));
        return execute(request);
    }

    public JSONObject pay(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        int cents = minorUnits(total);
        JSONObject body = new JSONObject();
        body.put("appid", weChatProperties.getAppid()); body.put("mchid", weChatProperties.getMchid());
        body.put("description", description); body.put("out_trade_no", orderNum); body.put("notify_url", weChatProperties.getNotifyUrl());
        JSONObject amount = new JSONObject(); amount.put("total", cents); amount.put("currency", "CNY"); body.put("amount", amount);
        JSONObject payer = new JSONObject(); payer.put("openid", openid); body.put("payer", payer);
        JSONObject response = parseSuccess(post(JSAPI, body));
        String prepayId = response.getString("prepay_id");
        if (blank(prepayId)) throw new WechatApiException(200, "MISSING_PREPAY_ID");
        String timestamp = Long.toString(System.currentTimeMillis() / 1000);
        String nonce = randomNonce();
        String packageValue = "prepay_id=" + prepayId;
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(merchantPrivateKey());
        signer.update((weChatProperties.getAppid() + "\n" + timestamp + "\n" + nonce + "\n" + packageValue + "\n").getBytes(StandardCharsets.UTF_8));
        JSONObject result = new JSONObject();
        result.put("timeStamp", timestamp); result.put("nonceStr", nonce); result.put("package", packageValue);
        result.put("signType", "RSA"); result.put("paySign", Base64.getEncoder().encodeToString(signer.sign()));
        return result;
    }

    public String refund(String outTradeNo, String outRefundNo, BigDecimal refund, BigDecimal total) throws Exception {
        int refundCents = minorUnits(refund), totalCents = minorUnits(total);
        if (refundCents > totalCents) throw new IllegalArgumentException("Refund exceeds order amount");
        JSONObject body = new JSONObject(); body.put("out_trade_no", outTradeNo); body.put("out_refund_no", outRefundNo);
        JSONObject amount = new JSONObject(); amount.put("refund", refundCents); amount.put("total", totalCents); amount.put("currency", "CNY");
        body.put("amount", amount); body.put("notify_url", weChatProperties.getRefundNotifyUrl()); return post(REFUNDS, body);
    }

    public String queryRefund(String outRefundNo) throws Exception { return execute(new HttpGet(REFUNDS + "/" + pathSegment(outRefundNo))); }
    public JSONObject queryOrder(String outTradeNo) throws Exception {
        return parseSuccess(execute(new HttpGet(new URIBuilder(ORDER_PATH + pathSegment(outTradeNo))
                .addParameter("mchid", requiredConfig(weChatProperties.getMchid())).build())));
    }
    public void closeOrder(String outTradeNo) throws Exception {
        JSONObject body = new JSONObject(); body.put("mchid", requiredConfig(weChatProperties.getMchid()));
        post(ORDER_PATH + pathSegment(outTradeNo) + "/close", body);
    }

    private static String pathSegment(String value) {
        if (blank(value) || !value.matches("[A-Za-z0-9_\\-\\|*]{1,64}")) throw new IllegalArgumentException("Invalid WeChat Pay order number");
        try { return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new IllegalStateException("UTF-8 unavailable"); }
    }
    private static JSONObject parseSuccess(String body) {
        try { JSONObject result = JSON.parseObject(body); if (result != null) return result; }
        catch (RuntimeException ignored) { }
        throw new WechatApiException(200, "INVALID_RESPONSE");
    }
    private static int minorUnits(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        try { return amount.movePointRight(2).intValueExact(); }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Payment amount requires exact integer cents within range"); }
    }
    private static String randomNonce() {
        byte[] bytes = new byte[16]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static PublicKey resolvePlatformPublicKey(WeChatProperties properties, String serial, Clock clock) {
        PublicKey key = loadPlatformTrust(properties, clock).keys.get(normalizeSerial(serial));
        if (key == null) throw new SecurityException("Unknown WeChat Pay signing key"); return key;
    }
    public static boolean verifyRsa(PublicKey key, byte[] message, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(key); verifier.update(message);
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException exception) { return false; }
    }

    private static PlatformTrust loadPlatformTrust(WeChatProperties properties, Clock clock) {
        PlatformTrust trust = new PlatformTrust();
        boolean hasId = !blank(properties.getPlatformPublicKeyId()), hasPath = !blank(properties.getPlatformPublicKeyFilePath());
        if (hasId != hasPath) throw new IllegalStateException("WeChat Pay public key ID and file must both be configured");
        try {
            if (hasId) {
                String id = properties.getPlatformPublicKeyId();
                if (!id.startsWith("PUB_KEY_ID_")) throw new IllegalStateException("Invalid WeChat Pay public key ID");
                String pem = new String(Files.readAllBytes(Paths.get(properties.getPlatformPublicKeyFilePath())), StandardCharsets.US_ASCII);
                String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
                PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
                trust.keys.put(id, key);
            }
            if (!blank(properties.getWeChatPayCertFilePath())) {
                try (InputStream input = Files.newInputStream(Paths.get(properties.getWeChatPayCertFilePath()))) {
                    Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509").generateCertificates(input);
                    if (certificates.isEmpty()) throw new IllegalStateException("Empty WeChat Pay certificate bundle");
                    for (Certificate certificate : certificates) {
                        X509Certificate x509 = (X509Certificate)certificate;
                        // Overlapping bundles may contain an expired predecessor; only trust currently valid keys.
                        try { x509.checkValidity(Date.from(clock.instant())); }
                        catch (java.security.cert.CertificateExpiredException | java.security.cert.CertificateNotYetValidException ignored) { continue; }
                        trust.keys.put(x509.getSerialNumber().toString(16).toUpperCase(Locale.ROOT), x509.getPublicKey()); trust.certificate = x509;
                    }
                }
            }
        } catch (Exception exception) { throw new IllegalStateException("WeChat Pay platform trust is not configured correctly"); }
        if (trust.keys.isEmpty()) throw new IllegalStateException("No valid WeChat Pay platform signing key configured"); return trust;
    }
    private static String normalizeSerial(String serial) {
        return serial == null || serial.startsWith("PUB_KEY_ID_") ? serial : serial.toUpperCase(Locale.ROOT);
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String requiredConfig(String value) {
        if (blank(value)) throw new IllegalStateException("WeChat Pay configuration is incomplete"); return value;
    }
    private static class PlatformTrust {
        final Map<String, PublicKey> keys = new HashMap<>(); X509Certificate certificate;
    }
}
