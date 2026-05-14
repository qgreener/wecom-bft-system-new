package com.wecombft.domain.enums;

public enum PaymentResult implements CodedEnum {
    SUCCESS,
    FAILED,
    PROCESSING,
    CLOSED;

    @Override
    public String code() {
        return name();
    }
}
