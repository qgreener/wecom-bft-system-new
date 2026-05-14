package com.wecombft.domain.enums;

public enum OrderRefundStatus implements CodedEnum {
    NONE,
    REVIEWING,
    REJECTED,
    PROCESSING,
    MANUAL_REQUIRED,
    FAILED,
    REFUNDED;

    @Override
    public String code() {
        return name();
    }
}
