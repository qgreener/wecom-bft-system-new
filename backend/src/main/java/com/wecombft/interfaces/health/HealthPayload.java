package com.wecombft.interfaces.health;

import java.time.OffsetDateTime;

public record HealthPayload(
    String status,
    String appName,
    String version,
    String environment,
    String integrationMode,
    OffsetDateTime serverTime,
    String traceId
) {
}
