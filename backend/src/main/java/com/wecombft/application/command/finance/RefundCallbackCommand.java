package com.wecombft.application.command.finance;

import java.time.LocalDateTime;
import java.util.Map;

public record RefundCallbackCommand(String eventNo, String refundNo, String externalRefundNo, String refundStatus, Long refundedAmountCent, LocalDateTime refundedAt, String failureReason, Map<String, Object> rawSnapshot) {
}
