package com.wecombft.application.command.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record MockPayCommand(
    String eventNo,
    String externalPaymentNo,
    Long paidAmountCent,
    String paymentResult,
    LocalDateTime paidAt,
    Map<String, Object> rawSnapshot
) {
}
