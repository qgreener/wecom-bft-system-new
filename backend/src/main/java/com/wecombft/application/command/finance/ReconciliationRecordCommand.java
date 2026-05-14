package com.wecombft.application.command.finance;

public record ReconciliationRecordCommand(String recordType, String merchantOrderNo, String externalTransactionNo, Long billAmountCent, Long feeAmountCent) {
}
