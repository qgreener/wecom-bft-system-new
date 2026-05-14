package com.wecombft.interfaces.dto.finance;

public record CompensationRetryResponse(long compensationId, String relatedObjectType, String processingStatus, String message) {
}
