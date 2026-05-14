package com.wecombft.domain.enums;

public enum OrderInvoiceStatus implements CodedEnum {
    NOT_APPLIED,
    APPLIED,
    TO_BE_ISSUED,
    ISSUED,
    RED_REVERSED;

    @Override
    public String code() {
        return name();
    }
}
