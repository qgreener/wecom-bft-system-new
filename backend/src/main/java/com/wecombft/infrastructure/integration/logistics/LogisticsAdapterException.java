package com.wecombft.infrastructure.integration.logistics;

public class LogisticsAdapterException extends RuntimeException {

    private final String errorCode;

    public LogisticsAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
