package com.wecombft.interfaces.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.system.SystemAdminUserService;
import com.wecombft.application.system.SystemAdminUserService.AdminUserView;
import com.wecombft.infrastructure.security.PermissionCatalog;
import com.wecombft.infrastructure.security.PermissionCatalog.RolePolicy;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class SystemAdminUserController {

    private final SystemAdminUserService service;
    private final PermissionCatalog permissionCatalog;

    public SystemAdminUserController(SystemAdminUserService service, PermissionCatalog permissionCatalog) {
        this.service = service;
        this.permissionCatalog = permissionCatalog;
    }

    @GetMapping("/api/admin/system/admin-users")
    @RequirePermission("system:config:read")
    public ResponseEntity<ApiResponse<List<AdminUserView>>> listUsers(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(keyword, status), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/system/admin-users/{user_id}/status")
    @RequirePermission("iam:role-grant")
    public ResponseEntity<ApiResponse<AdminUserView>> setStatus(
        @PathVariable("user_id") long userId,
        @RequestBody StatusBody body
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.setStatus(userId, body == null ? null : body.targetStatus()),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/system/role-matrix")
    @RequirePermission("system:config:read")
    public ResponseEntity<ApiResponse<List<RolePolicy>>> roleMatrix() {
        return ResponseEntity.ok(ApiResponse.ok(permissionCatalog.allPolicies(), TraceIds.currentOrCreate()));
    }

    public record StatusBody(String targetStatus) {
    }
}
