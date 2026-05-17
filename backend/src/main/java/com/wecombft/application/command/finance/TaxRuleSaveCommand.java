package com.wecombft.application.command.finance;

import java.math.BigDecimal;

public record TaxRuleSaveCommand(
    Long ruleId,
    String ruleName,
    String taxCategory,
    BigDecimal taxRate,
    String invoiceItemName,
    String status,
    String description
) {
}
