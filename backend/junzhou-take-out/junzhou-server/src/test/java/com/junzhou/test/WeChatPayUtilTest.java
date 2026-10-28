package com.junzhou.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.exception.WechatApiException;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.test.support.SignedWechatNotifications;
import com.junzhou.utils.WeChatPayUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.HttpVersion;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.methods.HttpGet;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WeChatPayUtilTest {
    @TempDir Path directory;
    private WeChatProperties properties;
    private CloseableHttpClient client;
    private CloseableHttpResponse response;
    private WeChatPayUtil util;
    private KeyPair merchant;

    @BeforeEach void setup() throws Exception {
        properties = new WeChatProperties();
        properties.setAppid("wx-test"); properties.setMchid("merchant-test");
        properties.setMchSerialNo("MERCHANT-SERIAL");
        properties.setNotifyUrl("https://example.invalid/pay");
        properties.setRefundNotifyUrl("https://example.invalid/refund");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        merchant = generator.generateKeyPair();
        Path key = directory.resolve("merchant-test.pem");
        Files.write(key, SignedWechatNotifications.pem("PRIVATE KEY", merchant.getPrivate().getEncoded()).getBytes(StandardCharsets.US_ASCII));
        properties.setPrivateKeyFilePath(key.toString());
        client = mock(CloseableHttpClient.class); response = mock(CloseableHttpResponse.class);
        when(client.execute(any(HttpUriRequest.class))).thenReturn(response);
        util = new WeChatPayUtil(properties) { @Override protected CloseableHttpClient getClient() { return client; } };
        reply(200, "{\"prepay_id\":\"wx-prepay-test\"}");
    }

    @Test void createsExactMinorUnitsAndVerifiableClientPaymentSignature() throws Exception {
        JSONObject result = util.pay("ORDER-1", new BigDecimal("12.34"), "晚餐", "openid-test");
        HttpUriRequest request = request();
        assertEquals(WeChatPayUtil.JSAPI, request.getURI().toString());
        assertNull(request.getFirstHeader("Wechatpay-Serial"));
        JSONObject payload = JSON.parseObject(EntityUtils.toString(((org.apache.http.client.methods.HttpPost) request).getEntity(), StandardCharsets.UTF_8));
        assertEquals(1234, payload.getJSONObject("amount").getIntValue("total"));
        assertEquals("wx-test", payload.getString("appid"));
        assertEquals("openid-test", payload.getJSONObject("payer").getString("openid"));
        assertEquals("prepay_id=wx-prepay-test", result.getString("package"));
        Signature verifier = Signature.getInstance("SHA256withRSA"); verifier.initVerify(merchant.getPublic());
        verifier.update(("wx-test\n"+result.getString("timeStamp")+"\n"+result.getString("nonceStr")+"\nprepay_id=wx-prepay-test\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(Base64.getDecoder().decode(result.getString("paySign"))));
        verify(response).close(); verify(client).close();
    }

    @ParameterizedTest @ValueSource(strings={"1.001", "21474836.48", "0", "-1"})
    void rejectsUnrepresentableOrNonPositiveAmountsBeforeNetwork(String amount) {
        assertThrows(IllegalArgumentException.class, () -> util.pay("ORDER-1", new BigDecimal(amount), "desc", "openid"));
        verifyNoInteractions(client);
        assertThrows(IllegalArgumentException.class, () -> util.refund("ORDER-1", "REFUND-1", new BigDecimal(amount), new BigDecimal("20")));
        verifyNoInteractions(client);
    }

    @Test void refusesPrepaymentWithoutPrepayId() throws Exception {
        reply(200, "{}");
        assertThrows(WechatApiException.class, () -> util.pay("ORDER-1", BigDecimal.ONE, "desc", "openid"));
    }

    @Test void exposesSafeHttpFailureClassificationAndClosesResources() throws Exception {
        reply(404, "{\"code\":\"RESOURCE_NOT_EXISTS\",\"message\":\"secret-value\",\"secret\":\"hidden\"}");
        WechatApiException exception = assertThrows(WechatApiException.class, () -> util.queryRefund("REFUND-1"));
        assertEquals(404, exception.getStatusCode()); assertEquals("RESOURCE_NOT_EXISTS", exception.getErrorCode());
        assertFalse(exception.toString().contains("secret-value")); assertFalse(exception.toString().contains("hidden"));
        verify(response).close(); verify(client).close();
    }

    @Test void queriesRefundAndOrderAndClosesUnpaidOrderWithCorrectMerchant() throws Exception {
        reply(200, "{\"status\":\"PROCESSING\"}");
        assertEquals("PROCESSING", JSON.parseObject(util.queryRefund("REFUND-1")).getString("status"));
        assertEquals("https://api.mch.weixin.qq.com/v3/refund/domestic/refunds/REFUND-1", request().getURI().toString());
        clearInvocations(client);
        reply(200, "{\"trade_state\":\"NOTPAY\"}");
        assertEquals("NOTPAY", util.queryOrder("ORDER-1").getString("trade_state"));
        assertEquals("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/ORDER-1?mchid=merchant-test", request().getURI().toString());
        clearInvocations(client);
        reply(204, "");
        util.closeOrder("ORDER-1");
        HttpUriRequest close = request();
        assertEquals("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/ORDER-1/close", close.getURI().toString());
        assertEquals("merchant-test", JSON.parseObject(EntityUtils.toString(((org.apache.http.client.methods.HttpPost)close).getEntity())).getString("mchid"));
    }

    @Test void refundCarriesExactAmountAndCallbackUrl() throws Exception {
        reply(200, "{\"status\":\"PROCESSING\"}");
        util.refund("ORDER-1", "REFUND-1", new BigDecimal("1.23"), new BigDecimal("2.34"));
        JSONObject payload = JSON.parseObject(EntityUtils.toString(((org.apache.http.client.methods.HttpPost)request()).getEntity()));
        assertEquals(123, payload.getJSONObject("amount").getIntValue("refund"));
        assertEquals(234, payload.getJSONObject("amount").getIntValue("total"));
        assertEquals("https://example.invalid/refund", payload.getString("notify_url"));
    }

    @Test void propertiesExcludeSecretsFromToStringAndRetainJsonCompatibility() {
        properties.setSecret("sensitive-secret"); properties.setApiV3Key("sensitive-api-key");
        assertFalse(properties.toString().contains("sensitive-secret"));
        assertFalse(properties.toString().contains("sensitive-api-key"));
        assertEquals("sensitive-secret", JSON.parseObject(JSON.toJSONString(properties)).getString("secret"));
    }

    @Test void officialSdkValidatesPinnedPublicKeyResponsesWithoutLoggingForgedBody() throws Exception {
        SignedWechatNotifications platform = new SignedWechatNotifications(properties, directory);
        AtomicBoolean forged = new AtomicBoolean(false);
        AtomicBoolean validRequestSignature = new AtomicBoolean(false);
        AtomicInteger responseStatus = new AtomicInteger(200);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/test", exchange -> {
            try {
                Map<String,String> authorization = new HashMap<>();
                String header = exchange.getRequestHeaders().getFirst("Authorization");
                if (header != null) {
                    Matcher matcher = Pattern.compile("(\\w+)=\"([^\"]*)\"").matcher(header);
                    while (matcher.find()) authorization.put(matcher.group(1), matcher.group(2));
                    Signature requestVerifier = Signature.getInstance("SHA256withRSA"); requestVerifier.initVerify(merchant.getPublic());
                    requestVerifier.update(("GET\n/test\n"+authorization.get("timestamp")+"\n"+authorization.get("nonce_str")+"\n\n").getBytes(StandardCharsets.UTF_8));
                    validRequestSignature.set("merchant-test".equals(authorization.get("mchid"))
                            && "MERCHANT-SERIAL".equals(authorization.get("serial_no"))
                            && requestVerifier.verify(Base64.getDecoder().decode(authorization.get("signature"))));
                }
                String body = "{\"sensitive\":\"never-log-this-body\"}";
                SignedWechatNotifications.Signed signed = platform.sign(body);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().add("Request-ID", "loopback-sdk-test");
                exchange.getResponseHeaders().add("Wechatpay-Serial", signed.serial);
                exchange.getResponseHeaders().add("Wechatpay-Timestamp", signed.timestamp);
                exchange.getResponseHeaders().add("Wechatpay-Nonce", signed.nonce);
                exchange.getResponseHeaders().add("Wechatpay-Signature", forged.get() ? "Zm9yZ2Vk" : signed.signature);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseStatus.get(), bytes.length); exchange.getResponseBody().write(bytes);
            } catch (Exception failure) { throw new java.io.IOException("Synthetic response failed"); }
            finally { exchange.close(); }
        });
        Logger logger = (Logger)LoggerFactory.getLogger("com.wechat.pay.contrib.apache.httpclient");
        ch.qos.logback.classic.Level originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        ListAppender<ILoggingEvent> appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
        server.start();
        try (CloseableHttpClient realClient = new RealClientUtil(properties).openClient()) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/test";
            try (CloseableHttpResponse valid = realClient.execute(new HttpGet(url))) {
                assertEquals(200, valid.getStatusLine().getStatusCode());
                assertTrue(EntityUtils.toString(valid.getEntity()).contains("never-log-this-body"));
                assertTrue(validRequestSignature.get());
            }
            forged.set(true);
            Exception exception = assertThrows(Exception.class, () -> {
                try (CloseableHttpResponse ignored = realClient.execute(new HttpGet(url))) { }
            });
            assertFalse(exception.toString().contains("never-log-this-body"));
            forged.set(false); responseStatus.set(400);
            try (CloseableHttpResponse failed = realClient.execute(new HttpGet(url))) {
                assertEquals(400, failed.getStatusLine().getStatusCode());
            }
            assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains("never-log-this-body")));
        } finally { server.stop(0); logger.detachAppender(appender); appender.stop(); logger.setLevel(originalLevel); }
    }

    private static class RealClientUtil extends WeChatPayUtil {
        RealClientUtil(WeChatProperties properties) { super(properties); }
        CloseableHttpClient openClient() { return super.getClient(); }
    }

    private void reply(int status, String body) throws Exception {
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(HttpVersion.HTTP_1_1, status, "test"));
        when(response.getEntity()).thenReturn(new StringEntity(body, StandardCharsets.UTF_8));
    }
    private HttpUriRequest request() throws Exception {
        ArgumentCaptor<HttpUriRequest> captor = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(client).execute(captor.capture()); return captor.getValue();
    }
}
