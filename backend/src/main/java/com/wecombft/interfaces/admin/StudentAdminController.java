package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.student.StudentAdminApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.student.StudentAdminDetailResponse;
import com.wecombft.interfaces.dto.student.StudentAdminPage;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class StudentAdminController {

    private final StudentAdminApplicationService service;

    public StudentAdminController(StudentAdminApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/students")
    @RequirePermission("student:read")
    public ResponseEntity<ApiResponse<StudentAdminPage>> adminStudents(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminStudents(AdminPrincipalContext.currentOrNull(), keyword, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/students/{student_id}")
    @RequirePermission("student:read")
    public ResponseEntity<ApiResponse<StudentAdminDetailResponse>> adminStudentDetail(@PathVariable("student_id") long studentId) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminStudentDetail(AdminPrincipalContext.currentOrNull(), studentId),
            TraceIds.currentOrCreate()));
    }
}
