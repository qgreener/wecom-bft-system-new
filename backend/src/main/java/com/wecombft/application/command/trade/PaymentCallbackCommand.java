package com.wecombft.application.command.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record PaymentCallbackCommand(
    String eventNo,
    String merchantOrderNo,
    String externalPaymentNo,
    Long paidAmountCent,
    String paymentResult,
    LocalDateTime paidAt,
    Map<String, Object> rawSnapshot
) {
}
