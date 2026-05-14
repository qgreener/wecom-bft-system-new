package com.wecombft.interfaces.dto.finance;

import java.util.Map;

public record AccountingWorkbenchSummaryResponse(String relatedMonth, long incomeAmountCent, long refundAmountCent, long purchaseAmountCent, long issuedInvoiceAmountCent, long redReversedInvoiceAmountCent, int reconciliationDiffCount, Map<String, Integer> materialStatusCounts) {
}
