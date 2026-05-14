package com.wecombft.application.command.student;

public record AppWechatLoginCommand(String wxCode, String sourceChannel) {
}
