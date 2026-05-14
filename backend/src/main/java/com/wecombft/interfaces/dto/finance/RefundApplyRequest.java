package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.RefundApplyCommand;

public record RefundApplyRequest(
    Long orderId,
    Long applyAmountCent,
    String refundReason,
    String applyDescription,
    String entitlementAction
) {
    public RefundApplyCommand toCommand() {
        return new RefundApplyCommand(
            orderId,
            applyAmountCent,
            refundReason,
            applyDescription,
            entitlementAction);
    }
}
