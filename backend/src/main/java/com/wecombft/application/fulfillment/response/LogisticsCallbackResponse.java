package com.wecombft.application.fulfillment.response;

public record LogisticsCallbackResponse(String processingStatus, Long shipmentId, Long orderId, String trackingNo, String status, String failureReason) {
}
