package com.wecombft.application.command.learning;

import java.time.LocalDateTime;

public record RefundEntitlementCommand(
    long studentId,
    long orderId,
    long courseId,
    long refundId,
    String action,
    LocalDateTime occurredAt
) {
}
