package com.wecombft.application.command.finance;

import java.time.LocalDateTime;

public record InvoiceIssueCommand(String invoiceNo, String invoiceFile, LocalDateTime issuedAt, String remark) {
}
