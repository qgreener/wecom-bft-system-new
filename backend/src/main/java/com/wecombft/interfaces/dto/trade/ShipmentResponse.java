package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record ShipmentResponse(
    long shipmentId,
    String shipmentNo,
    String status,
    Map<String, Object> receiverSnapshot,
    boolean exceptionFlag,
    String exceptionReason,
    LocalDateTime createdAt
) {
}
