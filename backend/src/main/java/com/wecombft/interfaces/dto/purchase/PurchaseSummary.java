package com.wecombft.interfaces.dto.purchase;

import java.time.LocalDateTime;

public record PurchaseSummary(long purchaseId, String purchaseNo, long supplierId, long totalAmountCent, String purchaseStatus, String inputInvoiceStatus, Long approvalId, LocalDateTime createdAt) {
}
