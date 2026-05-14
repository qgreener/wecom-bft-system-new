package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.RoleApplicationService;
import com.wecombft.application.iam.RoleApplicationService.ApprovalActionCommand;
import com.wecombft.application.iam.RoleApplicationService.ApprovalResponse;
import com.wecombft.application.iam.RoleApplicationService.RoleApplicationCommand;
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
        @RequestBody RoleApplicationCommand command
    ) {
        ApprovalResponse response = roleApplicationService.submit(authorization, idempotencyKey, command);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/collab/approvals/{approval_id}/actions")
    @RequirePermission("iam:role-application:approve")
    public ResponseEntity<ApiResponse<ApprovalResponse>> action(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("approval_id") long approvalId,
        @RequestBody ApprovalActionCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            roleApplicationService.action(authorization, approvalId, command),
            TraceIds.currentOrCreate()));
    }
}
