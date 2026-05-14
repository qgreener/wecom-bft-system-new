package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.AccountingMaterialCloseCommand;

public record AccountingMaterialCloseRequest(
    String closedReason
) {
    public AccountingMaterialCloseCommand toCommand() {
        return new AccountingMaterialCloseCommand(
            closedReason);
    }
}
