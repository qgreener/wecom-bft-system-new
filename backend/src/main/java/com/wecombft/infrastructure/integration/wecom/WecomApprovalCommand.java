package com.wecombft.infrastructure.integration.wecom;

import java.util.List;

public record WecomApprovalCommand(
    String creatorWecomUserId,
    String title,
    String description,
    List<String> approverWecomUserIds
) {
}
