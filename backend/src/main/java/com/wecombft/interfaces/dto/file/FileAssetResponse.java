package com.wecombft.interfaces.dto.file;

import java.time.LocalDateTime;

public record FileAssetResponse(
    long fileId,
    String fileNo,
    String fileName,
    String fileType,
    String storageKey,
    String accessUrl,
    String fileDigest,
    long fileSize,
    String bizType,
    long bizId,
    Long orderId,
    String status,
    Long uploadedBy,
    LocalDateTime uploadedAt
) {
}
