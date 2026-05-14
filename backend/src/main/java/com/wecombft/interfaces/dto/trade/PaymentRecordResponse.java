package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;

public record PaymentRecordResponse(
    long paymentId,
    String paymentNo,
    String channel,
    String paymentMethod,
    String paymentResult,
    long paidAmountCent,
    String externalPaymentNo,
    LocalDateTime paidAt,
    String callbackEventNo,
    String idempotencyKey,
    String failureReason
) {
}
