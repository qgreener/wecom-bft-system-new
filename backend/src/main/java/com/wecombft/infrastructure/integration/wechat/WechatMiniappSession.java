package com.wecombft.infrastructure.integration.wechat;

public record WechatMiniappSession(
    String openid,
    String unionid,
    String sessionKey
) {
}
