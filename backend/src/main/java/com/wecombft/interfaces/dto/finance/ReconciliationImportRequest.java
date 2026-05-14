package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.ReconciliationImportCommand;
import java.util.List;

public record ReconciliationImportRequest(
    String billMonth,
    String billSource,
    String fileName,
    String fileDigest,
    List<ReconciliationRecordRequest> records
) {
    public ReconciliationImportCommand toCommand() {
        return new ReconciliationImportCommand(
            billMonth,
            billSource,
            fileName,
            fileDigest,
            records == null ? null : records.stream().map(ReconciliationRecordRequest::toCommand).toList());
    }
}
