package com.wecombft.application.command.purchase;

public record PurchaseInputInvoiceCommand(String inputInvoiceNo, Long inputInvoiceAmountCent, String inputInvoiceFile) {
}
