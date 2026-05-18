package com.wecombft.interfaces.dto.mock;

public record MockTriggerResponse(
    String mockEventNo,
    String sceneCode,
    String callbackPath,
    String processingStatus,
    String integrationCallbackEventNo,
    String message
) {
}
