package com.wecombft.interfaces.dto.purchase;

import java.time.LocalDateTime;

public record ReceiptRow(long id, String receiptNo, long purchaseId, String purchaseNo, String inboundBatchNo, String status, LocalDateTime receivedAt, long receiverUserId, String stockFlowIds, String remark) {
}
