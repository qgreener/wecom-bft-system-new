package com.wecombft.application.command.finance;

import java.time.LocalDateTime;
import java.util.Map;

public record InvoiceRedReverseCallbackCommand(String eventNo, String invoiceApplyNo, String status, String redInvoiceNo, String redInvoiceFile, LocalDateTime redReversedAt, String failureReason, Map<String, Object> rawSnapshot) {
}
