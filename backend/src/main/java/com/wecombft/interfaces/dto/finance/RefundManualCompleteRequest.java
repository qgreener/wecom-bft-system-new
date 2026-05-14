package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.RefundManualCompleteCommand;
import java.time.LocalDateTime;

public record RefundManualCompleteRequest(
    String refundChannel,
    String manualVoucherNo,
    String manualVoucherFile,
    LocalDateTime refundedAt,
    String entitlementAction,
    String remark
) {
    public RefundManualCompleteCommand toCommand() {
        return new RefundManualCompleteCommand(
            refundChannel,
            manualVoucherNo,
            manualVoucherFile,
            refundedAt,
            entitlementAction,
            remark);
    }
}
