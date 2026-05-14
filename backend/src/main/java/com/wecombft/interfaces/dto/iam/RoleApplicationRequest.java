package com.wecombft.interfaces.dto.iam;

import com.wecombft.application.command.iam.RoleApplicationCommand;

public record RoleApplicationRequest(
    String roleCode,
    String submitReason
) {
    public RoleApplicationCommand toCommand() {
        return new RoleApplicationCommand(
            roleCode,
            submitReason);
    }
}
