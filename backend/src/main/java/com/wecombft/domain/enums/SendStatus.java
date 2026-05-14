package com.wecombft.domain.enums;

public enum SendStatus implements CodedEnum {
    PENDING,
    SENT,
    FAILED,
    CANCELED;

    @Override
    public String code() {
        return name();
    }
}
