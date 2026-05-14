package com.wecombft.application.command.finance;

public record RefundApplyCommand(Long orderId, Long applyAmountCent, String refundReason, String applyDescription, String entitlementAction) {
}
