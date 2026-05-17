package com.wecombft.infrastructure.security;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.persistence.iam.RoleRecord;
import com.wecombft.infrastructure.persistence.iam.UserRecord;
import com.wecombft.shared.web.ApiException;

@Service
public class AdminSessionService {

    private static final String TOKEN_PREFIX = "S3-DEMO-";

    private final IamRepository iamRepository;
    private final PermissionCatalog permissionCatalog;
    private final AuditLogService auditLogService;

    public AdminSessionService(
        IamRepository iamRepository,
        PermissionCatalog permissionCatalog,
        AuditLogService auditLogService
    ) {
        this.iamRepository = iamRepository;
        this.permissionCatalog = permissionCatalog;
        this.auditLogService = auditLogService;
    }

    public String issueTestToken(String userNo) {
        UserRecord user = iamRepository.findActiveUserByUserNo(userNo)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "用户不存在或已禁用"));
        return TOKEN_PREFIX + user.userNo();
    }

    public AdminPrincipal require(String authorizationHeader) {
        String userNo = parseBearerToken(authorizationHeader);
        UserRecord user = iamRepository.findActiveUserByUserNo(userNo)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录态无效"));
        List<RoleRecord> roles = iamRepository.findActiveRolesByUserId(user.id());
        return new AdminPrincipal(
            user,
            roles,
            permissionCatalog.merge(roles.stream().map(RoleRecord::roleCode).toList())
        );
    }

    public AdminPrincipal requirePermission(String authorizationHeader, String permissionCode) {
        AdminPrincipal principal = require(authorizationHeader);
        if (!principal.permissionView().permissionCodes().contains(permissionCode)) {
            auditLogService.writeFailure(
                principal,
                "SECURITY",
                "PERMISSION_DENIED",
                "PERMISSION",
                null,
                permissionCode,
                null,
                "缺少权限：" + permissionCode);
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限访问该资源");
        }
        return principal;
    }

    public void requireAnyPermission(AdminPrincipal principal, List<String> permissionCodes) {
        boolean allowed = permissionCodes.stream().anyMatch(principal.permissionView().permissionCodes()::contains);
        if (!allowed) {
            String joined = String.join(",", permissionCodes);
            String targetNo = joined.length() > 60 ? joined.substring(0, 57) + "..." : joined;
            auditLogService.writeFailure(
                principal,
                "SECURITY",
                "PERMISSION_DENIED",
                "PERMISSION",
                null,
                targetNo,
                null,
                "缺少任一权限：" + joined);
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限访问该资源");
        }
    }

    private String parseBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少登录态");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (!token.startsWith(TOKEN_PREFIX) || token.length() == TOKEN_PREFIX.length()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录态无效");
        }
        return token.substring(TOKEN_PREFIX.length());
    }
}
