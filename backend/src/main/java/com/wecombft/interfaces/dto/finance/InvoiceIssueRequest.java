package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.InvoiceIssueCommand;
import java.time.LocalDateTime;

public record InvoiceIssueRequest(
    String invoiceNo,
    String invoiceFile,
    LocalDateTime issuedAt,
    String remark
) {
    public InvoiceIssueCommand toCommand() {
        return new InvoiceIssueCommand(
            invoiceNo,
            invoiceFile,
            issuedAt,
            remark);
    }
}
