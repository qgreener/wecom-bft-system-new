package com.wecombft.interfaces.dto.trade;

import java.time.LocalDateTime;

public record AuditLogSummary(
    long auditId,
    String operationModule,
    String operationType,
    String targetType,
    String targetNo,
    String result,
    String failureReason,
    LocalDateTime occurredAt
) {
}
