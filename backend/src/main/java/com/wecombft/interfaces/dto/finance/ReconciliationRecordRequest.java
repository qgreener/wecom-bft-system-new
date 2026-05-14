package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.ReconciliationRecordCommand;

public record ReconciliationRecordRequest(
    String recordType,
    String merchantOrderNo,
    String externalTransactionNo,
    Long billAmountCent,
    Long feeAmountCent
) {
    public ReconciliationRecordCommand toCommand() {
        return new ReconciliationRecordCommand(
            recordType,
            merchantOrderNo,
            externalTransactionNo,
            billAmountCent,
            feeAmountCent);
    }
}
