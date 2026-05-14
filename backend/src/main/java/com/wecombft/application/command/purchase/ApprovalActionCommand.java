package com.wecombft.application.command.purchase;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ApprovalActionCommand(String action, @JsonAlias("approval_comment") String comment) {
}
