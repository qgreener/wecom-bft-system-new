package com.wecombft.interfaces.dto.collab;

import java.time.LocalDateTime;

public record ApprovalActionResponse(
    long approvalId,
    String approvalNo,
    String approvalType,
    String status,
    String approvalComment,
    LocalDateTime finishedAt,
    String relatedObjectType,
    Long relatedObjectId,
    Object businessResult
) {
}
