package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.InvoiceIssueCallbackCommand;
import java.time.LocalDateTime;
import java.util.Map;

public record InvoiceIssueCallbackRequest(
    String eventNo,
    String invoiceApplyNo,
    String status,
    String invoiceNo,
    String invoiceFile,
    LocalDateTime issuedAt,
    String failureReason,
    Map<String, Object> rawSnapshot
) {
    public InvoiceIssueCallbackCommand toCommand() {
        return new InvoiceIssueCallbackCommand(
            eventNo,
            invoiceApplyNo,
            status,
            invoiceNo,
            invoiceFile,
            issuedAt,
            failureReason,
            rawSnapshot);
    }
}
