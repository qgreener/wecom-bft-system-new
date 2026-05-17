package com.wecombft.interfaces.dto.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TaxRuleResponse(
    long ruleId,
    String ruleNo,
    String ruleName,
    String taxCategory,
    BigDecimal taxRate,
    String invoiceItemName,
    String status,
    String description,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
