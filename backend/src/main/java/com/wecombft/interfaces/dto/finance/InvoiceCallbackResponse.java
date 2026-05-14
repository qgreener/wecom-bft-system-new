package com.wecombft.interfaces.dto.finance;

public record InvoiceCallbackResponse(String processingStatus, Long invoiceId, String invoiceApplyNo, String status, String failureReason) {
}
