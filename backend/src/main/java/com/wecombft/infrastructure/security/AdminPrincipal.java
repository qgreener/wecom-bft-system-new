package com.wecombft.infrastructure.security;

import java.util.List;

import com.wecombft.infrastructure.persistence.iam.RoleRecord;
import com.wecombft.infrastructure.persistence.iam.UserRecord;

public record AdminPrincipal(
    UserRecord user,
    List<RoleRecord> roles,
    PermissionCatalog.PermissionView permissionView
) {

    public long userId() {
        return user.id();
    }

    public String userNo() {
        return user.userNo();
    }

    public String displayName() {
        return user.displayName();
    }

    public List<String> roleCodes() {
        return roles.stream().map(RoleRecord::roleCode).toList();
    }

    public boolean hasRoles() {
        return !roles.isEmpty();
    }
}
