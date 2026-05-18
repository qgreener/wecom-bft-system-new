package com.wecombft.application.command.finance;

public record CompensationActionCommand(
    String compensationType,
    String action,
    String retryMode,
    String failureReason,
    String closedReason,
    String remark
) {
}
