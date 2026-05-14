package com.wecombft.application.command.crm;

import java.time.LocalDateTime;

public record LeadPaidConversionCommand(
    Long studentId,
    String mobile,
    String wecomExternalUserId,
    Long orderId,
    LocalDateTime paidAt
) {
}
