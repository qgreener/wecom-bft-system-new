package com.wecombft.interfaces.dto.student;

import com.wecombft.application.command.student.PhoneAuthorizeCommand;

public record PhoneAuthorizeRequest(
    String phoneCode
) {
    public PhoneAuthorizeCommand toCommand() {
        return new PhoneAuthorizeCommand(
            phoneCode);
    }
}
