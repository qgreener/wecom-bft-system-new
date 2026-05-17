package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.ReconciliationCheckCommand;

public record ReconciliationCheckRequest(
    String differenceReason,
    Boolean checkedFlag,
    String remark
) {
    public ReconciliationCheckCommand toCommand() {
        return new ReconciliationCheckCommand(differenceReason, checkedFlag, remark);
    }
}
