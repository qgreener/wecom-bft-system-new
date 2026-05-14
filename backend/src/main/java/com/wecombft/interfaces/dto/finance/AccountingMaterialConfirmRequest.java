package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.AccountingMaterialConfirmCommand;

public record AccountingMaterialConfirmRequest(
    String remark
) {
    public AccountingMaterialConfirmCommand toCommand() {
        return new AccountingMaterialConfirmCommand(
            remark);
    }
}
