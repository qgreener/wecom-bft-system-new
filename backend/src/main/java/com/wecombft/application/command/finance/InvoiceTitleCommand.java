package com.wecombft.application.command.finance;

public record InvoiceTitleCommand(String titleType, String titleName, String taxNo, String email, Boolean isDefault) {
}
