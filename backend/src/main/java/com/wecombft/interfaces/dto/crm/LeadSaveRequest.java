package com.wecombft.interfaces.dto.crm;

import com.wecombft.application.command.crm.LeadSaveCommand;
import java.time.LocalDateTime;

public record LeadSaveRequest(
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
    public LeadSaveCommand toCommand() {
        return new LeadSaveCommand(
            leadId,
            name,
            mobile,
            sourceChannel,
            sourceCode,
            intentCourseId,
            ownerUserId,
            wecomExternalUserId,
            nextFollowAt,
            remark);
    }
}
