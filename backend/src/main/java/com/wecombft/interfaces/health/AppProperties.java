package com.wecombft.interfaces.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String name,
    String version,
    String environment,
    boolean maintenanceMode
) {
}
