package com.wecombft.domain.enums;

public enum InputInvoiceStatus implements CodedEnum {
    NOT_INVOICED,
    INVOICED;

    @Override
    public String code() {
        return name();
    }
}
