package com.wecombft.interfaces.dto.purchase;

import java.time.LocalDateTime;
import java.util.List;

public record PurchaseReceiptResponse(long receiptId, String receiptNo, long purchaseId, String purchaseNo, String status, LocalDateTime receivedAt, List<Long> stockFlowIds, String purchaseStatus) {
}
