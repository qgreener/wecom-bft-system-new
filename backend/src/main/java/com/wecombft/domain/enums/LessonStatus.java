package com.wecombft.domain.enums;

public enum LessonStatus implements CodedEnum {
    DRAFT,
    PUBLISHED,
    HIDDEN;

    @Override
    public String code() {
        return name();
    }
}
