package com.wecombft.application.command.finance;

import java.time.LocalDateTime;

public record AccountingMaterialCreateCommand(String materialType, String relatedMonth, Long orderId, String relatedObjectType, Long relatedObjectId, String purpose, LocalDateTime dueAt) {
}
