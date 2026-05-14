package com.wecombft.interfaces.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "file")
public record FileStorageProperties(
    String storageType,
    String uploadBasePath,
    int maxUploadSizeMb
) {
}
