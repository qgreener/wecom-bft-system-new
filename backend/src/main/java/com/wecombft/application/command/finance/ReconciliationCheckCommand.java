package com.wecombft.application.command.finance;

public record ReconciliationCheckCommand(
    String differenceReason,
    Boolean checkedFlag,
    String remark
) {
}
