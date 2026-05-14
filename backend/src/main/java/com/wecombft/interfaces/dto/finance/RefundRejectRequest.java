package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.RefundRejectCommand;

public record RefundRejectRequest(
    String rejectReason
) {
    public RefundRejectCommand toCommand() {
        return new RefundRejectCommand(
            rejectReason);
    }
}
