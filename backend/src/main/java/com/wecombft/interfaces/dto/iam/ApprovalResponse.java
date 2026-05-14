package com.wecombft.interfaces.dto.iam;

import java.time.LocalDateTime;

public record ApprovalResponse(
    long approvalId,
    String approvalNo,
    String approvalType,
    String title,
    String roleCode,
    String status,
    LocalDateTime submittedAt,
    LocalDateTime finishedAt
) {
}
