package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.RoleApplicationService;
import com.wecombft.interfaces.dto.iam.ApprovalResponse;
import com.wecombft.interfaces.dto.iam.RoleApplicationRequest;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class RoleApplicationController {

    private final RoleApplicationService roleApplicationService;

    public RoleApplicationController(RoleApplicationService roleApplicationService) {
        this.roleApplicationService = roleApplicationService;
    }

    @PostMapping("/api/admin/role-applications")
    @RequirePermission("iam:role-application:create")
    public ResponseEntity<ApiResponse<ApprovalResponse>> submit(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody RoleApplicationRequest command
    ) {
        ApprovalResponse response = roleApplicationService.submit(authorization, idempotencyKey, command == null ? null : command.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(response, TraceIds.currentOrCreate()));
    }

}
