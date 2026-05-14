package com.wecombft.interfaces.dto.crm;

import com.wecombft.application.command.crm.LeadStatusCommand;
import java.time.LocalDateTime;

public record LeadStatusRequest(
    String targetStatus,
    String abandonReason,
    LocalDateTime nextFollowAt
) {
    public LeadStatusCommand toCommand() {
        return new LeadStatusCommand(
            targetStatus,
            abandonReason,
            nextFollowAt);
    }
}
