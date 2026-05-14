package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.RefundApproveCommand;

public record RefundApproveRequest(
    Long approvedAmountCent,
    String refundChannel,
    String reviewComment,
    String entitlementAction
) {
    public RefundApproveCommand toCommand() {
        return new RefundApproveCommand(
            approvedAmountCent,
            refundChannel,
            reviewComment,
            entitlementAction);
    }
}
