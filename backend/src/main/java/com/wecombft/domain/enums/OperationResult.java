package com.wecombft.domain.enums;

public enum OperationResult implements CodedEnum {
    SUCCESS,
    FAILED;

    @Override
    public String code() {
        return name();
    }
}
