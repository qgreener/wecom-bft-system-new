package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.InvoiceApplyCommand;

public record InvoiceApplyRequest(
    Long orderId,
    Long titleId,
    String email
) {
    public InvoiceApplyCommand toCommand() {
        return new InvoiceApplyCommand(
            orderId,
            titleId,
            email);
    }
}
