package com.wecombft.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wecom")
public record WecomProperties(
    String corpId,
    String agentId,
    String agentSecret,
    String oauthRedirectUri
) {
}
