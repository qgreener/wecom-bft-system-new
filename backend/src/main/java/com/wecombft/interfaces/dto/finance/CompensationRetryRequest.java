package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.CompensationRetryCommand;

public record CompensationRetryRequest(
    String compensationType,
    String action,
    String remark
) {
    public CompensationRetryCommand toCommand() {
        return new CompensationRetryCommand(
            compensationType,
            action,
            remark);
    }
}
