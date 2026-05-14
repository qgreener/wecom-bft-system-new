package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.CollabApplicationService;
import com.wecombft.application.iam.CollabApplicationService.ApprovalPage;
import com.wecombft.application.iam.CollabApplicationService.NotificationPage;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/collab")
public class CollabController {

    private final CollabApplicationService collabApplicationService;

    public CollabController(CollabApplicationService collabApplicationService) {
        this.collabApplicationService = collabApplicationService;
    }

    @GetMapping("/approvals")
    public ResponseEntity<ApiResponse<ApprovalPage>> approvals(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "role", required = false) String role,
        @RequestParam(value = "approval_type", required = false) String approvalType,
        @RequestParam(value = "status", required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            collabApplicationService.approvals(authorization, role, approvalType, status),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<NotificationPage>> notifications(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "scene_code", required = false) String sceneCode
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            collabApplicationService.notifications(authorization, sceneCode),
            TraceIds.currentOrCreate()));
    }
}
