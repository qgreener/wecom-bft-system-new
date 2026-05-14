package com.wecombft.interfaces.dto.trade;

import com.wecombft.application.command.trade.MockPayCommand;
import java.time.LocalDateTime;
import java.util.Map;

public record MockPayRequest(
    String eventNo,
    String externalPaymentNo,
    Long paidAmountCent,
    String paymentResult,
    LocalDateTime paidAt,
    Map<String, Object> rawSnapshot
) {
    public MockPayCommand toCommand() {
        return new MockPayCommand(
            eventNo,
            externalPaymentNo,
            paidAmountCent,
            paymentResult,
            paidAt,
            rawSnapshot);
    }
}
