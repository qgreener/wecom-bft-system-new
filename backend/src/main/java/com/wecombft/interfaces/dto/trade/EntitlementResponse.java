package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;
import java.util.Map;

public record EntitlementResponse(
    long entitlementId,
    String entitlementNo,
    long courseId,
    long specId,
    String status,
    LocalDateTime openedAt,
    Map<String, Object> courseSnapshot
) {
}
