package com.wecombft.application.command.finance;

import java.time.LocalDateTime;

public record RefundManualCompleteCommand(String refundChannel, String manualVoucherNo, String manualVoucherFile, LocalDateTime refundedAt, String entitlementAction, String remark) {
}
