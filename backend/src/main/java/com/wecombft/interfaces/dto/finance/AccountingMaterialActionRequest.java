package com.wecombft.interfaces.dto.finance;

import java.util.List;

public record AccountingMaterialActionRequest(String action, List<String> fileRefs, String closedReason, String remark) {
}
