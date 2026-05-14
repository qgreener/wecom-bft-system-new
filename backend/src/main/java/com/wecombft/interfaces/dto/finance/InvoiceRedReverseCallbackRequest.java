package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.InvoiceRedReverseCallbackCommand;
import java.time.LocalDateTime;
import java.util.Map;

public record InvoiceRedReverseCallbackRequest(
    String eventNo,
    String invoiceApplyNo,
    String status,
    String redInvoiceNo,
    String redInvoiceFile,
    LocalDateTime redReversedAt,
    String failureReason,
    Map<String, Object> rawSnapshot
) {
    public InvoiceRedReverseCallbackCommand toCommand() {
        return new InvoiceRedReverseCallbackCommand(
            eventNo,
            invoiceApplyNo,
            status,
            redInvoiceNo,
            redInvoiceFile,
            redReversedAt,
            failureReason,
            rawSnapshot);
    }
}
