package com.wecombft.infrastructure.persistence.audit;

import java.time.LocalDateTime;

public record AuditLogRecord(
    long id,
    String traceId,
    Long operatorUserId,
    String operatorName,
    String operationModule,
    String operationType,
    String targetType,
    Long targetId,
    String targetNo,
    Long orderId,
    String result,
    String failureReason,
    LocalDateTime occurredAt
) {
}
