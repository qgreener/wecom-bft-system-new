package com.wecombft.application.command.crm;

import java.time.LocalDateTime;

public record LeadStatusCommand(String targetStatus, String abandonReason, LocalDateTime nextFollowAt) {
}
