package com.wecombft.interfaces.dto.crm;

import com.wecombft.application.command.crm.PublicLeadCommand;

public record PublicLeadRequest(
    String name,
    String mobile,
    String sourceCode,
    Long intentCourseId
) {
    public PublicLeadCommand toCommand() {
        return new PublicLeadCommand(
            name,
            mobile,
            sourceCode,
            intentCourseId);
    }
}
