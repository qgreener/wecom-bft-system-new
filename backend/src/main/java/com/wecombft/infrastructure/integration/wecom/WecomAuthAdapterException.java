package com.wecombft.infrastructure.integration.wecom;

public class WecomAuthAdapterException extends RuntimeException {

    private final String errorCode;

    public WecomAuthAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WecomAuthAdapterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
