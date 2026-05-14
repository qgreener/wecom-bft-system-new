package com.wecombft.application.command.finance;

import java.time.LocalDateTime;

public record InvoiceRedReverseCommand(String redInvoiceNo, String redInvoiceFile, LocalDateTime redReversedAt, String remark) {
}
