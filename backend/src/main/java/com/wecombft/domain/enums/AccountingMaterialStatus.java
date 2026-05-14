package com.wecombft.domain.enums;

public enum AccountingMaterialStatus implements CodedEnum {
    PENDING_SUPPLEMENT,
    UPLOADED,
    CONFIRMED,
    CLOSED;

    @Override
    public String code() {
        return name();
    }
}
