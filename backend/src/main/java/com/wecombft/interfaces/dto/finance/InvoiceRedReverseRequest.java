package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.InvoiceRedReverseCommand;
import java.time.LocalDateTime;

public record InvoiceRedReverseRequest(
    String redInvoiceNo,
    String redInvoiceFile,
    LocalDateTime redReversedAt,
    String remark
) {
    public InvoiceRedReverseCommand toCommand() {
        return new InvoiceRedReverseCommand(
            redInvoiceNo,
            redInvoiceFile,
            redReversedAt,
            remark);
    }
}
