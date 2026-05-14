package com.wecombft.domain.enums;

public enum SpecStatus implements CodedEnum {
    ENABLED,
    DISABLED;

    @Override
    public String code() {
        return name();
    }
}
