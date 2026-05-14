package com.wecombft.interfaces.dto.fulfillment;

import java.time.LocalDateTime;
import java.util.List;

public record ShipmentActionResponse(
    long shipmentId,
    String shipmentNo,
    long orderId,
    String status,
    String logisticsCompanyName,
    String trackingNo,
    LocalDateTime shippedAt,
    LocalDateTime signedAt,
    List<Long> stockFlowIds,
    boolean exceptionFlag,
    String exceptionReason
) {
}
