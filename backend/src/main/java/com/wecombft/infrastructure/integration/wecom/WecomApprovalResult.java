package com.wecombft.infrastructure.integration.wecom;

public record WecomApprovalResult(
    boolean success,
    String spNo,
    String failureReason
) {

    public static WecomApprovalResult success(String spNo) {
        return new WecomApprovalResult(true, spNo, null);
    }

    public static WecomApprovalResult failure(String reason) {
        return new WecomApprovalResult(false, null, reason);
    }
}
