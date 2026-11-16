package com.junzhou.exception;

public class MiniappApiException extends RuntimeException {
    private final int status;
    public MiniappApiException(int status, String message) { super(message); this.status=status; }
    public int getStatus() { return status; }
}
