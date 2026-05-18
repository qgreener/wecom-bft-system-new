package com.wecombft.interfaces.dto.collab;

public record ApprovalActionRequest(
    String action,
    String approvalComment,
    String rejectReason
) {
}
