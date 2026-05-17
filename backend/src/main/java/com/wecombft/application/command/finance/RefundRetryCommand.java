package com.wecombft.application.command.finance;

public record RefundRetryCommand(
    String retryMode,
    String remark
) {
}
