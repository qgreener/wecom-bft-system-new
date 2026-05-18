package com.wecombft.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wechat.miniapp")
public record WechatMiniappProperties(
    String appId,
    String appSecret,
    String code2sessionUrl
) {
}
