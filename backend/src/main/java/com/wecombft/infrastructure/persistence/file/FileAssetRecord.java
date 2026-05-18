package com.wecombft.infrastructure.persistence.file;

import java.time.LocalDateTime;

public record FileAssetRecord(
    long id,
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
    LocalDateTime uploadedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
