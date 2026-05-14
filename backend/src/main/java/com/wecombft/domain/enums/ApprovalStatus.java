package com.wecombft.domain.enums;

public enum ApprovalStatus implements CodedEnum {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED;

    @Override
    public String code() {
        return name();
    }
}
