package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.course.CourseApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.course.EntitlementAdminPage;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class EntitlementAdminController {

    private final CourseApplicationService service;

    public EntitlementAdminController(CourseApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/entitlements")
    @RequirePermission("learning:entitlement:read")
    public ResponseEntity<ApiResponse<EntitlementAdminPage>> adminEntitlements(
        @RequestParam(value = "student_id", required = false) Long studentId,
        @RequestParam(value = "course_id", required = false) Long courseId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminEntitlements(AdminPrincipalContext.currentOrNull(), studentId, courseId, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }
}
