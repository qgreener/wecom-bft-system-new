package com.wecombft.infrastructure.integration.wechat;

public class WechatMiniappAuthException extends RuntimeException {

    private final String errorCode;

    public WechatMiniappAuthException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public WechatMiniappAuthException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
