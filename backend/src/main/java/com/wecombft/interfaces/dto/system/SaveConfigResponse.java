package com.wecombft.interfaces.dto.system;

import java.util.List;
import java.time.LocalDateTime;

public record SaveConfigResponse(
    String configGroup,
    List<String> updatedKeys,
    long version,
    LocalDateTime updatedAt,
    long auditLogId
) {
}
