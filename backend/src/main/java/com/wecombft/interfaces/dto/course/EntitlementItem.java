package com.wecombft.interfaces.dto.course;

import java.time.LocalDateTime;

public record EntitlementItem(
    long entitlementId,
    String entitlementNo,
    long orderId,
    String orderNo,
    long courseId,
    long specId,
    String status,
    LocalDateTime openedAt,
    LocalDateTime expireAt,
    boolean remindStopped,
    String courseSnapshot
) {
}
