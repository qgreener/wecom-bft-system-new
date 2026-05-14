package com.wecombft.shared.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_ARGUMENT("INVALID_ARGUMENT", "Invalid request argument.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Authentication is required.", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Permission denied.", HttpStatus.FORBIDDEN),
    NOT_FOUND("NOT_FOUND", "Resource not found.", HttpStatus.NOT_FOUND),
    STATE_CONFLICT("STATE_CONFLICT", "Resource state conflict.", HttpStatus.CONFLICT),
    BUSINESS_RULE_BLOCKED("BUSINESS_RULE_BLOCKED", "Business rule blocked the operation.", HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR("INTERNAL_ERROR", "Internal server error.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String defaultMessage;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String defaultMessage, HttpStatus httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
