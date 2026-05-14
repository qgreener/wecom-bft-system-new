package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.InvoiceTitleCommand;

public record InvoiceTitleRequest(
    String titleType,
    String titleName,
    String taxNo,
    String email,
    Boolean isDefault
) {
    public InvoiceTitleCommand toCommand() {
        return new InvoiceTitleCommand(
            titleType,
            titleName,
            taxNo,
            email,
            isDefault);
    }
}
