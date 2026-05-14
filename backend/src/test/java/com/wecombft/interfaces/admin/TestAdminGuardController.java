package com.wecombft.interfaces.admin;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;

@RestController
class TestAdminGuardController {

    @GetMapping("/api/admin/test-guard/protected")
    Map<String, String> protectedEndpoint() {
        return Map.of("status", "reachable");
    }

    @RequirePermission("system:audit:read")
    @GetMapping("/api/admin/test-guard/audit-only")
    Map<String, String> auditOnlyEndpoint() {
        return Map.of("status", "audit");
    }

    @RequireAnyPermission({"course:lesson:write", "course:spec:write"})
    @GetMapping("/api/admin/test-guard/course-write")
    Map<String, String> courseWriteEndpoint() {
        return Map.of("status", "course");
    }
}
