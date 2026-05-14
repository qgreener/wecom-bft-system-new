package com.wecombft.interfaces.dto.trade;

public record PaymentCallbackResponse(
    String processingStatus,
    Long orderId,
    Long paymentId,
    String paymentNo,
    String paymentStatus,
    String idempotencyKey,
    String failureReason
) {
}
