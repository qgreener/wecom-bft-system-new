package com.wecombft.infrastructure.persistence.trade;

import java.time.LocalDateTime;

public record OrderDocumentLinkRecord(
        long id,
        long orderId,
        String orderNo,
        String documentType,
        long documentId,
        String documentNo,
        String documentStatus,
        Long amountCent,
        String relationType,
        LocalDateTime occurredAt,
        String sourceTable,
        String remark) {
}
