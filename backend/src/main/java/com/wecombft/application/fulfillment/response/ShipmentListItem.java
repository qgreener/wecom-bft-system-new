package com.wecombft.application.fulfillment.response;

import java.time.LocalDateTime;

public record ShipmentListItem(
    long shipmentId,
    String shipmentNo,
    long orderId,
    String orderNo,
    long studentId,
    String status,
    String logisticsCompanyName,
    String trackingNo,
    LocalDateTime shippedAt,
    LocalDateTime signedAt,
    boolean exceptionFlag,
    String exceptionReason
) {
}
