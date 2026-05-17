package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.RefundRetryCommand;

public record RefundRetryRequest(
    String retryMode,
    String remark
) {
    public RefundRetryCommand toCommand() {
        return new RefundRetryCommand(retryMode, remark);
    }
}
