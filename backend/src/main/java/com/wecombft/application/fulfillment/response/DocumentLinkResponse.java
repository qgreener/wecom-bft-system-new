package com.wecombft.application.fulfillment.response;

import java.time.LocalDateTime;

public record DocumentLinkResponse(
    long documentLinkId,
    String documentType,
    long documentId,
    String documentNo,
    String documentStatus,
    Long amountCent,
    String relationType,
    LocalDateTime occurredAt,
    String sourceTable,
    String remark
) {
}
