package com.wecombft.interfaces.dto.finance;

import java.util.List;

public record ReconciliationBatchResponse(long batchId, String batchNo, String billMonth, String billSource, String fileName, String importStatus, int totalCount, int matchedCount, int diffCount, List<ReconciliationRecordResponse> records) {
}
