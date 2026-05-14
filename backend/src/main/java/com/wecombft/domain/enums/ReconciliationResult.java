package com.wecombft.domain.enums;

public enum ReconciliationResult implements CodedEnum {
    MATCHED,
    AMOUNT_DIFF,
    FEE_DIFF,
    UNMATCHED,
    DUPLICATE;

    @Override
    public String code() {
        return name();
    }
}
