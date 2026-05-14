package com.wecombft.interfaces.dto.finance;

public record RefundReviewRequest(
    String action,
    Long approvedAmountCent,
    String reviewComment,
    String rejectReason,
    String refundChannel,
    String entitlementAction
) {
}
