package com.wecombft.interfaces.dto.finance;

public record ReconciliationRecordResponse(long recordId, long batchId, String recordType, Long orderId, String orderNo, Long paymentId, Long refundId, String merchantOrderNo, String externalTransactionNo, Long systemAmountCent, Long billAmountCent, Long feeAmountCent, String result, String differenceReason) {
}
