package com.wecombft.infrastructure.integration.wecom;

public class WecomApprovalAdapterException extends RuntimeException {

    private final String errorCode;

    public WecomApprovalAdapterException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WecomApprovalAdapterException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
