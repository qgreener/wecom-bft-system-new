package com.wecombft.infrastructure.persistence.system;

import java.time.LocalDateTime;

public record SystemConfigRecord(
    long id,
    String configGroup,
    String configKey,
    String displayName,
    String configValue,
    String maskedValue,
    boolean sensitive,
    boolean editable,
    LocalDateTime updatedAt,
    Long updatedBy,
    long version
) {
}
