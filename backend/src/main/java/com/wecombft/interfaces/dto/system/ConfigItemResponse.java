package com.wecombft.interfaces.dto.system;

import java.time.LocalDateTime;

public record ConfigItemResponse(
    String configKey,
    String displayName,
    String maskedValue,
    boolean editableFlag,
    LocalDateTime updatedAt,
    Long updatedBy,
    long version
) {
}
