package com.wecombft.interfaces.dto.finance;

import com.wecombft.application.command.finance.AccountingMaterialCreateCommand;
import java.time.LocalDateTime;

public record AccountingMaterialCreateRequest(
    String materialType,
    String relatedMonth,
    Long orderId,
    String relatedObjectType,
    Long relatedObjectId,
    String purpose,
    LocalDateTime dueAt
) {
    public AccountingMaterialCreateCommand toCommand() {
        return new AccountingMaterialCreateCommand(
            materialType,
            relatedMonth,
            orderId,
            relatedObjectType,
            relatedObjectId,
            purpose,
            dueAt);
    }
}
