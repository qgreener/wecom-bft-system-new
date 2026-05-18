package com.wecombft.interfaces.dto.mock;

import java.util.Map;

public record MockTriggerRequest(
    String targetNo,
    Map<String, Object> payloadOverride,
    String triggerReason
) {
}
