package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.AccountingMaterialUploadCommand;
import java.util.List;

public record AccountingMaterialUploadRequest(
    List<String> fileRefs,
    String remark
) {
    public AccountingMaterialUploadCommand toCommand() {
        return new AccountingMaterialUploadCommand(
            fileRefs,
            remark);
    }
}
