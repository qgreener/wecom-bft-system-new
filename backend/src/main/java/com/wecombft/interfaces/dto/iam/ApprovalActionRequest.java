package com.wecombft.interfaces.dto.iam;

import com.wecombft.application.command.iam.ApprovalActionCommand;

public record ApprovalActionRequest(
    String action,
    String approvalComment
) {
    public ApprovalActionCommand toCommand() {
        return new ApprovalActionCommand(
            action,
            approvalComment);
    }
}
