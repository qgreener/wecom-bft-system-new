package com.wecombft.interfaces.dto.fulfillment;

public record LogisticsCallbackResponse(String processingStatus, Long shipmentId, Long orderId, String trackingNo, String status, String failureReason) {
}
