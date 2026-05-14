package com.wecombft.application.command.finance;

import java.time.LocalDateTime;
import java.util.Map;

public record InvoiceIssueCallbackCommand(String eventNo, String invoiceApplyNo, String status, String invoiceNo, String invoiceFile, LocalDateTime issuedAt, String failureReason, Map<String, Object> rawSnapshot) {
}
