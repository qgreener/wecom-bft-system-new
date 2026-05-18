package com.wecombft.infrastructure.integration.wecom;

public class WecomCardAdapterException extends RuntimeException {

    private final String errorCode;

    public WecomCardAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WecomCardAdapterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
