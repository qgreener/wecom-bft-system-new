package com.wecombft.domain.enums;

public enum ReadStatus implements CodedEnum {
    UNREAD,
    READ;

    @Override
    public String code() {
        return name();
    }
}
