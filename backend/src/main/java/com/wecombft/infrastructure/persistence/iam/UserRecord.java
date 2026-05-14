package com.wecombft.infrastructure.persistence.iam;

public record UserRecord(
    long id,
    String userNo,
    String displayName,
    String status
) {
}
