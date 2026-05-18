package com.wecombft.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wecom.callback")
public record WecomCallbackProperties(
    String token,
    String aesKey
) {
}
