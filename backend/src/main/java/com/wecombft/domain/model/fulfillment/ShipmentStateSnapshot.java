package com.wecombft.domain.model.fulfillment;

public record ShipmentStateSnapshot(
    String paymentStatus,
    String orderFulfillmentStatus,
    String refundStatus,
    String shipmentStatus
) {
}
