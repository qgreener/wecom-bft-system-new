package com.wecombft.domain.enums;

public enum PaymentStatus implements CodedEnum {
    PENDING,
    PAID,
    CLOSED;

    @Override
    public String code() {
        return name();
    }
}
