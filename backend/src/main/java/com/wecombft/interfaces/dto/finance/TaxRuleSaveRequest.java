package com.wecombft.interfaces.dto.finance;

import java.math.BigDecimal;

import com.wecombft.application.command.finance.TaxRuleSaveCommand;

public record TaxRuleSaveRequest(
    Long ruleId,
    String ruleName,
    String taxCategory,
    BigDecimal taxRate,
    String invoiceItemName,
    String status,
    String description
) {
    public TaxRuleSaveCommand toCommand() {
        return new TaxRuleSaveCommand(
            ruleId,
            ruleName,
            taxCategory,
            taxRate,
            invoiceItemName,
            status,
            description);
    }
}
