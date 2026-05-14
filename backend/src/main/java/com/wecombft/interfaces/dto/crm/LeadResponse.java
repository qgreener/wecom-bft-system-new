package com.wecombft.interfaces.dto.crm;

import java.time.LocalDateTime;

public record LeadResponse(
    long leadId,
    String leadNo,
    String name,
    String mobile,
    String sourceChannel,
    String sourceCode,
    Long intentCourseId,
    Long ownerUserId,
    String wecomExternalUserId,
    String status,
    boolean matchExceptionFlag,
    LocalDateTime nextFollowAt,
    LocalDateTime latestFollowAt,
    Long studentId,
    Long convertedOrderId,
    String abandonReason
) {
}
