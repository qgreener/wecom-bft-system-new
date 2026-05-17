package com.wecombft.interfaces.dto.dashboard;

import java.util.List;

public record DashboardTodosResponse(
    long pendingRefundReviewCount,
    long pendingShipmentCount,
    long pendingInvoiceIssueCount,
    long pendingRedReverseCount,
    long pendingReconciliationDiffCount,
    long pendingPurchaseApprovalCount,
    List<TodoSample> pendingRefundReviewSamples,
    List<TodoSample> pendingShipmentSamples,
    List<TodoSample> pendingInvoiceIssueSamples,
    List<TodoSample> pendingRedReverseSamples,
    List<TodoSample> pendingReconciliationDiffSamples,
    List<TodoSample> pendingPurchaseApprovalSamples
) {
    public record TodoSample(
        long entityId,
        String entityNo,
        String summary,
        String status,
        Long amountCent,
        Long orderId,
        String orderNo,
        java.time.LocalDateTime occurredAt
    ) {
    }
}
