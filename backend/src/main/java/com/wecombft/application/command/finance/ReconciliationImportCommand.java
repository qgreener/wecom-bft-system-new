package com.wecombft.application.command.finance;

import java.util.List;

public record ReconciliationImportCommand(String billMonth, String billSource, String fileName, String fileDigest, List<ReconciliationRecordCommand> records) {
}
