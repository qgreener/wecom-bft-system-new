package com.wecombft.interfaces.dto.finance;

import java.time.LocalDateTime;

public record InvoiceResponse(long invoiceId, String invoiceApplyNo, long orderId, String orderNo, long invoiceAmountCent, String status, String invoiceChannel, String invoiceNo, String invoiceFile, LocalDateTime issuedAt, Long sourceRefundId, String redInvoiceNo, String redInvoiceFile, LocalDateTime redReversedAt, String failureReason) {
}
