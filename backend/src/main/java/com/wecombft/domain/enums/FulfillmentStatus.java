package com.wecombft.domain.enums;

public enum FulfillmentStatus implements CodedEnum {
    NO_SHIPMENT,
    PENDING_SHIPMENT,
    SHIPPED,
    SIGNED;

    @Override
    public String code() {
        return name();
    }
}
