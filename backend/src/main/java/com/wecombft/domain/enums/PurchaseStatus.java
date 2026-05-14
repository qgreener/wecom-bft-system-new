package com.wecombft.domain.enums;

public enum PurchaseStatus implements CodedEnum {
    APPROVING,
    APPROVAL_REJECTED,
    WAIT_CONFIRM,
    CONFIRMED,
    SHIPPED,
    REJECTED,
    COMPLETED,
    CANCELED;

    @Override
    public String code() {
        return name();
    }
}
