package com.wecombft.infrastructure.persistence.iam;

public record RoleRecord(
    long id,
    String roleCode,
    String roleName,
    String dataScope
) {
}
