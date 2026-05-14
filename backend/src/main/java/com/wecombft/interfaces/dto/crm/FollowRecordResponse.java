package com.wecombft.interfaces.dto.crm;

import java.time.LocalDateTime;

public record FollowRecordResponse(
    long followRecordId,
    long leadId,
    String followMethod,
    String content,
    LocalDateTime nextFollowAt,
    String status
) {
}
