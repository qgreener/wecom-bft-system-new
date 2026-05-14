package com.wecombft.shared.web;

import org.springframework.http.HttpStatus;

import com.wecombft.shared.error.ErrorCode;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode.httpStatus(), errorCode.code(), message);
    }

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
