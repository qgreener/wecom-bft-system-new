package com.wecombft.interfaces.health;

import java.time.OffsetDateTime;
import java.util.List;

public record ReadinessPayload(
    String status,
    List<DependencyCheckPayload> checks,
    OffsetDateTime serverTime,
    String traceId
) {
}
