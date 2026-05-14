package com.wecombft.interfaces.dto.purchase;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.wecombft.application.command.purchase.ApprovalActionCommand;

public record ApprovalActionRequest(
    String action,
    @JsonAlias("approval_comment") String comment
) {
    public ApprovalActionCommand toCommand() {
        return new ApprovalActionCommand(
            action,
            comment);
    }
}
