package com.wecombft.domain.enums;

public enum CourseStatus implements CodedEnum {
    DRAFT,
    PENDING_REVIEW,
    ON_SHELF,
    OFF_SHELF,
    DELETE_PENDING,
    DELETED;

    @Override
    public String code() {
        return name();
    }
}
