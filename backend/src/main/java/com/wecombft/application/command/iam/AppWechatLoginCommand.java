package com.wecombft.application.command.iam;

public record AppWechatLoginCommand(String wxCode, String sourceChannel) {
}
