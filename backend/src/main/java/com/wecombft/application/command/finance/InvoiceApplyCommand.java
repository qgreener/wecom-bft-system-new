package com.wecombft.application.command.finance;

public record InvoiceApplyCommand(Long orderId, Long titleId, String email) {
}
