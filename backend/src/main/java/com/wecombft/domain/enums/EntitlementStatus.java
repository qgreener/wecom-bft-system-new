package com.wecombft.domain.enums;

public enum EntitlementStatus implements CodedEnum {
    ACTIVE,
    FROZEN,
    REVOKED;

    @Override
    public String code() {
        return name();
    }
}
