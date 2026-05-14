package com.wecombft.application.command.crm;

import java.time.LocalDateTime;

public record LeadSaveCommand(
    Long leadId,
    String name,
    String mobile,
    String sourceChannel,
    String sourceCode,
    Long intentCourseId,
    Long ownerUserId,
    String wecomExternalUserId,
    LocalDateTime nextFollowAt,
    String remark
) {
}
