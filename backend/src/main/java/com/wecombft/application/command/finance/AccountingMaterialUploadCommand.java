package com.wecombft.application.command.finance;

import java.util.List;

public record AccountingMaterialUploadCommand(List<String> fileRefs, String remark) {
}
