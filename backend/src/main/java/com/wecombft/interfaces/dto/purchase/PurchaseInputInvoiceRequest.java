package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.PurchaseInputInvoiceCommand;

public record PurchaseInputInvoiceRequest(
    String inputInvoiceNo,
    Long inputInvoiceAmountCent,
    String inputInvoiceFile
) {
    public PurchaseInputInvoiceCommand toCommand() {
        return new PurchaseInputInvoiceCommand(
            inputInvoiceNo,
            inputInvoiceAmountCent,
            inputInvoiceFile);
    }
}
