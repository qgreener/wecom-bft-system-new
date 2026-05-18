package com.wecombft.infrastructure.mock;

import java.util.Map;

public record MockTriggerContext(
    String sceneCode,
    String capability,
    String targetNo,
    Map<String, Object> payloadOverride,
    String triggerReason,
    String idempotencyKey,
    String eventNo
) {
}
