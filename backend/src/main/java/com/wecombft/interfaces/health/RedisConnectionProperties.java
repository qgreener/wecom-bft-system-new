package com.wecombft.interfaces.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.data.redis")
public record RedisConnectionProperties(
    String host,
    int port,
    String password,
    String timeout
) {
}
