package com.wecombft.domain.enums;

public enum LeadStatus implements CodedEnum {
    PENDING_FOLLOW,
    CONTACTED,
    CONVERTED,
    ABANDONED;

    @Override
    public String code() {
        return name();
    }
}
