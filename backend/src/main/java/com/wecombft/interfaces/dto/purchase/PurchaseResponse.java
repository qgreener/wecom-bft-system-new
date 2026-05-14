package com.wecombft.interfaces.dto.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseResponse(
    long purchaseId,
    String purchaseNo,
    long supplierId,
    long applicantUserId,
    long totalAmountCent,
    String purchaseStatus,
    String inputInvoiceStatus,
    Long approvalId,
    Long thresholdSnapshotCent,
    LocalDate expectedArrivalDate,
    LocalDateTime supplierConfirmAt,
    String supplierRejectReason,
    String logisticsCompanyName,
    String trackingNo,
    LocalDateTime receivedAt,
    Long receiverUserId,
    String inputInvoiceNo,
    Long inputInvoiceAmountCent,
    String inputInvoiceFile,
    List<PurchaseItemResponse> purchaseItems,
    List<ReceiptRow> receipts,
    List<StockFlowResponse> stockFlows
) {
}
