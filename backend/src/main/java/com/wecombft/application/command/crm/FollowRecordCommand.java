package com.wecombft.application.command.crm;

import java.time.LocalDateTime;

public record FollowRecordCommand(String followMethod, String content, LocalDateTime nextFollowAt) {
}
