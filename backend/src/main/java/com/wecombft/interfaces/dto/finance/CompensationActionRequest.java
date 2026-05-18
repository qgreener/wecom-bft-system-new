package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.CompensationActionCommand;

public record CompensationActionRequest(
    String compensationType,
    String action,
    String retryMode,
    String failureReason,
    String closedReason,
    String remark
) {
    public CompensationActionCommand toCommand() {
        return new CompensationActionCommand(
            compensationType,
            action,
            retryMode,
            failureReason,
            closedReason,
            remark);
    }
}
