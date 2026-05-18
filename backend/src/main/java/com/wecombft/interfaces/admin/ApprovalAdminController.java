package com.wecombft.interfaces.admin;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.course.CourseApplicationService;
import com.wecombft.application.iam.RoleApplicationService;
import com.wecombft.application.purchase.PurchaseApplicationService;
import com.wecombft.application.command.course.CourseApprovalActionCommand;
import com.wecombft.infrastructure.persistence.iam.ApprovalRecord;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.interfaces.dto.collab.ApprovalActionRequest;
import com.wecombft.interfaces.dto.collab.ApprovalActionResponse;
import com.wecombft.interfaces.dto.course.CourseApprovalActionResponse;
import com.wecombft.interfaces.dto.iam.ApprovalResponse;
import com.wecombft.interfaces.dto.purchase.PurchaseResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiException;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class ApprovalAdminController {

    private final IamRepository iamRepository;
    private final CourseApplicationService courseApplicationService;
    private final PurchaseApplicationService purchaseApplicationService;
    private final RoleApplicationService roleApplicationService;

    public ApprovalAdminController(
        IamRepository iamRepository,
        CourseApplicationService courseApplicationService,
        PurchaseApplicationService purchaseApplicationService,
        RoleApplicationService roleApplicationService
    ) {
        this.iamRepository = iamRepository;
        this.courseApplicationService = courseApplicationService;
        this.purchaseApplicationService = purchaseApplicationService;
        this.roleApplicationService = roleApplicationService;
    }

    @PostMapping("/api/collab/approvals/{approval_id}/actions")
    @RequireAnyPermission({
        "course:approval:approve",
        "purchase:approval:approve",
        "iam:role-application:approve"
    })
    public ResponseEntity<ApiResponse<ApprovalActionResponse>> approvalAction(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("approval_id") long approvalId,
        @RequestBody ApprovalActionRequest command
    ) {
        ApprovalRecord approval = iamRepository.findApprovalById(approvalId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "审批记录不存在"));
        ApprovalActionResponse response = dispatch(authorization, approval, command);
        return ResponseEntity.ok(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    private ApprovalActionResponse dispatch(
        String authorization,
        ApprovalRecord approval,
        ApprovalActionRequest command
    ) {
        String approvalType = approval.approvalType();
        if (approvalType == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "审批类型缺失");
        }
        return switch (approvalType) {
            case "ROLE_APPLICATION" -> dispatchRoleApplication(authorization, approval, command);
            case "PURCHASE_LARGE", "PURCHASE_APPROVAL" -> dispatchPurchase(approval, command);
            case "COURSE_PUBLISH", "COURSE_OFFLINE", "COURSE_DELETE", "COURSE_APPROVAL" -> dispatchCourse(approval, command);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
                "未知审批类型: " + approvalType);
        };
    }

    private ApprovalActionResponse dispatchRoleApplication(String authorization, ApprovalRecord approval, ApprovalActionRequest command) {
        com.wecombft.application.command.iam.ApprovalActionCommand cmd =
            new com.wecombft.application.command.iam.ApprovalActionCommand(command.action(), command.approvalComment());
        ApprovalResponse response = roleApplicationService.action(authorization, approval.id(), cmd);
        return new ApprovalActionResponse(
            response.approvalId(),
            response.approvalNo(),
            response.approvalType(),
            response.status(),
            command.approvalComment(),
            response.finishedAt(),
            approval.relatedObjectType(),
            approval.relatedObjectId(),
            response);
    }

    private ApprovalActionResponse dispatchPurchase(ApprovalRecord approval, ApprovalActionRequest command) {
        com.wecombft.application.command.purchase.ApprovalActionCommand cmd =
            new com.wecombft.application.command.purchase.ApprovalActionCommand(command.action(), command.approvalComment());
        PurchaseResponse response = purchaseApplicationService.approvePurchase(
            AdminPrincipalContext.currentOrNull(), approval.id(), cmd);
        return new ApprovalActionResponse(
            approval.id(),
            approval.approvalNo(),
            approval.approvalType(),
            "APPROVE".equalsIgnoreCase(command.action()) ? "APPROVED" : "REJECTED",
            command.approvalComment(),
            LocalDateTime.now(),
            approval.relatedObjectType(),
            approval.relatedObjectId(),
            response);
    }

    private ApprovalActionResponse dispatchCourse(ApprovalRecord approval, ApprovalActionRequest command) {
        CourseApprovalActionCommand cmd = new CourseApprovalActionCommand(command.action(), command.approvalComment());
        CourseApprovalActionResponse response = courseApplicationService.approvalAction(approval.id(), cmd);
        return new ApprovalActionResponse(
            approval.id(),
            approval.approvalNo(),
            approval.approvalType(),
            "APPROVE".equalsIgnoreCase(command.action()) ? "APPROVED" : "REJECTED",
            command.approvalComment(),
            LocalDateTime.now(),
            approval.relatedObjectType(),
            approval.relatedObjectId(),
            response);
    }
}
