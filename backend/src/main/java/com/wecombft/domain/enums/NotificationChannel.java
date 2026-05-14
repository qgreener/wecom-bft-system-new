package com.wecombft.domain.enums;

public enum NotificationChannel implements CodedEnum {
    IN_APP,
    WECHAT_SUBSCRIBE,
    WECOM_CARD;

    @Override
    public String code() {
        return name();
    }
}
