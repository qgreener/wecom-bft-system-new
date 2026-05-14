package com.wecombft.interfaces.dto.trade;

import com.wecombft.application.command.trade.PaymentCallbackCommand;
import java.time.LocalDateTime;
import java.util.Map;

public record PaymentCallbackRequest(
    String eventNo,
    String merchantOrderNo,
    String externalPaymentNo,
    Long paidAmountCent,
    String paymentResult,
    LocalDateTime paidAt,
    Map<String, Object> rawSnapshot
) {
    public PaymentCallbackCommand toCommand() {
        return new PaymentCallbackCommand(
            eventNo,
            merchantOrderNo,
            externalPaymentNo,
            paidAmountCent,
            paymentResult,
            paidAt,
            rawSnapshot);
    }
}
