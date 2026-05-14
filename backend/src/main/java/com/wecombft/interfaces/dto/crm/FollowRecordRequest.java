package com.wecombft.interfaces.dto.crm;

import com.wecombft.application.command.crm.FollowRecordCommand;
import java.time.LocalDateTime;

public record FollowRecordRequest(
    String followMethod,
    String content,
    LocalDateTime nextFollowAt
) {
    public FollowRecordCommand toCommand() {
        return new FollowRecordCommand(
            followMethod,
            content,
            nextFollowAt);
    }
}
