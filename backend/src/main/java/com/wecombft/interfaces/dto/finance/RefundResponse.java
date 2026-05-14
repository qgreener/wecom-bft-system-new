package com.wecombft.interfaces.dto.finance;

import java.time.LocalDateTime;

public record RefundResponse(long refundId, String refundNo, long orderId, String orderNo, long applyAmountCent, Long approvedAmountCent, String status, String refundChannel, String externalRefundNo, String manualVoucherNo, String manualVoucherFile, String failureReason, String entitlementAction, LocalDateTime refundedAt) {
}
