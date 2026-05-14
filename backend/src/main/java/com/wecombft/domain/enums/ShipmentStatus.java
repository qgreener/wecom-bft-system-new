package com.wecombft.domain.enums;

public enum ShipmentStatus implements CodedEnum {
    PENDING_SHIPMENT,
    SHIPPED,
    SIGNED;

    @Override
    public String code() {
        return name();
    }
}
