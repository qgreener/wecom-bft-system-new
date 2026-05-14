package com.wecombft.domain.enums;

public enum LessonNodeType implements CodedEnum {
    CHAPTER,
    LESSON;

    @Override
    public String code() {
        return name();
    }
}
