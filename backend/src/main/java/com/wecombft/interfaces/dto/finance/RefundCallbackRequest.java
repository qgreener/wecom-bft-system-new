package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.RefundCallbackCommand;
import java.time.LocalDateTime;
import java.util.Map;

public record RefundCallbackRequest(
    String eventNo,
    String refundNo,
    String externalRefundNo,
    String refundStatus,
    Long refundedAmountCent,
    LocalDateTime refundedAt,
    String failureReason,
    Map<String, Object> rawSnapshot
) {
    public RefundCallbackCommand toCommand() {
        return new RefundCallbackCommand(
            eventNo,
            refundNo,
            externalRefundNo,
            refundStatus,
            refundedAmountCent,
            refundedAt,
            failureReason,
            rawSnapshot);
    }
}
