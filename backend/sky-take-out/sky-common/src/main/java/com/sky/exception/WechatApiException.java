package com.sky.exception;

/** Safe failure metadata. Never retains provider payloads, signatures or credentials. */
public class WechatApiException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;
    public WechatApiException(int statusCode, String errorCode) {
        super("WeChat Pay request failed (HTTP " + statusCode + ", code " + safeCode(errorCode) + ")");
        this.statusCode = statusCode; this.errorCode = safeCode(errorCode);
    }
    private static String safeCode(String code) {
        return code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "UNKNOWN_ERROR";
    }
    public int getStatusCode() { return statusCode; }
    public String getErrorCode() { return errorCode; }
}
