package com.wecombft.interfaces.dto.crm;

public record LeadPaidConversionResponse(
    boolean matched,
    Long leadId,
    String leadNo,
    String status,
    Long studentId,
    Long orderId,
    boolean idempotentHit,
    String missReason
) {
}
