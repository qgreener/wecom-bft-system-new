package com.wecombft.application.command.finance;

public record RefundApproveCommand(Long approvedAmountCent, String refundChannel, String reviewComment, String entitlementAction) {
}
