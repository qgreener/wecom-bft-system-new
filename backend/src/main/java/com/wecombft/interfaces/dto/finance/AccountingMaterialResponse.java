package com.wecombft.interfaces.dto.finance;

import java.util.List;
import java.time.LocalDateTime;

public record AccountingMaterialResponse(long materialId, String materialNo, String materialType, String status, String relatedMonth, Long orderId, String orderNo, String relatedObjectType, Long relatedObjectId, String purpose, List<String> fileRefs, LocalDateTime dueAt, LocalDateTime uploadedAt, LocalDateTime confirmedAt) {
}
