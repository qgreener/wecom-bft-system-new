package com.wecombft.application.command.learning;

import java.time.LocalDateTime;

public record PaymentSuccessEntitlementCommand(
    long studentId,
    long userId,
    long orderId,
    String orderNo,
    long orderItemId,
    long courseId,
    long specId,
    String courseSnapshotJson,
    LocalDateTime openedAt
) {
}
