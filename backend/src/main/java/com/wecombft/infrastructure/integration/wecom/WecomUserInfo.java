package com.wecombft.infrastructure.integration.wecom;

public record WecomUserInfo(
    String wecomUserId,
    String openId,
    String displayName,
    String mobile
) {
}
