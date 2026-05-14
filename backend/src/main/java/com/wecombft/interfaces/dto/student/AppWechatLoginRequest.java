package com.wecombft.interfaces.dto.student;

import com.wecombft.application.command.student.AppWechatLoginCommand;

public record AppWechatLoginRequest(
    String wxCode,
    String sourceChannel
) {
    public AppWechatLoginCommand toCommand() {
        return new AppWechatLoginCommand(
            wxCode,
            sourceChannel);
    }
}
