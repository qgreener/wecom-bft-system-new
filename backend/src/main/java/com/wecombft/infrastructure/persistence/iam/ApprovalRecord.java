package com.wecombft.infrastructure.persistence.iam;

import java.time.LocalDateTime;

public record ApprovalRecord(
    long id,
    String approvalNo,
    String approvalType,
    String title,
    long applicantUserId,
    Long approverUserId,
    String relatedObjectNo,
    String status,
    String submitReason,
    String approvalComment,
    LocalDateTime submittedAt,
    LocalDateTime finishedAt
) {
}
