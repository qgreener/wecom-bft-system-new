package com.wecombft.infrastructure.persistence.integration;

import java.time.LocalDateTime;

public record CallbackEventRecord(
        long id,
        String eventNo,
        String sourceSystem,
        String eventType,
        String idempotencyKey,
        Long orderId,
        String relatedObjectType,
        Long relatedObjectId,
        String relatedObjectNo,
        String processingStatus,
        int retryCount,
        String failureReason,
        String rawSnapshot,
        LocalDateTime receivedAt,
        LocalDateTime processedAt) {
}
