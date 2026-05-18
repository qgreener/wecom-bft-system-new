package com.wecombft.infrastructure.mock;

public record MockDispatchResult(
    String callbackPath,
    String processingStatus,
    String integrationCallbackEventNo,
    String message
) {
}
