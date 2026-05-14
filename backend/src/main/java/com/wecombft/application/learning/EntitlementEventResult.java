package com.wecombft.application.learning;

public record EntitlementEventResult(
    long entitlementId,
    String entitlementNo,
    String status,
    boolean idempotentHit
) {
}
