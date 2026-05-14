package com.wecombft.application.iam;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.persistence.iam.ApprovalRecord;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.persistence.iam.RoleRecord;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class RoleApplicationService {

    private final AdminSessionService adminSessionService;
    private final IamRepository iamRepository;
    private final AuditLogService auditLogService;
    private final IdGenerator idGenerator;

    public RoleApplicationService(
        AdminSessionService adminSessionService,
        IamRepository iamRepository,
        AuditLogService auditLogService,
        IdGenerator idGenerator
    ) {
        this.adminSessionService = adminSessionService;
        this.iamRepository = iamRepository;
        this.auditLogService = auditLogService;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public ApprovalResponse submit(String authorizationHeader, String idempotencyKey, RoleApplicationCommand command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "缺少 Idempotency-Key");
        }
        AdminPrincipal principal = adminSessionService.require(authorizationHeader);
        if (principal.hasRoles()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "已分配角色用户不能重复申请角色");
        }
        RoleRecord role = iamRepository.findActiveRoleByCode(command.roleCode())
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "申请角色不存在或已停用"));
        ApprovalRecord approval = iamRepository.findPendingRoleApplication(principal.userId(), role.roleCode())
            .orElseGet(() -> iamRepository.insertRoleApplication(
                idGenerator.nextId(),
                "APR" + idGenerator.nextId(),
                principal.user(),
                role,
                command.submitReason()));

        auditLogService.writeSuccess(
            principal,
            "IAM",
            "ROLE_APPLICATION_SUBMIT",
            "ROLE_APPLICATION",
            approval.id(),
            approval.approvalNo(),
            null,
            "{\"role_code\":\"" + role.roleCode() + "\"}");
        return toResponse(approval);
    }

    @Transactional
    public ApprovalResponse action(String authorizationHeader, long approvalId, ApprovalActionCommand command) {
        AdminPrincipal approver = adminSessionService.requirePermission(authorizationHeader, "iam:role-application:approve");
        ApprovalRecord approval = iamRepository.findApprovalById(approvalId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "审批记录不存在"));
        if (!"PENDING".equals(approval.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "审批记录不是待审批状态");
        }
        String action = command.action() == null ? "" : command.action().trim().toUpperCase();
        String status = switch (action) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "审批动作不支持");
        };

        ApprovalRecord finished = iamRepository.finishApproval(approvalId, approver.userId(), status, command.approvalComment());
        if ("APPROVED".equals(status)) {
            RoleRecord role = iamRepository.findActiveRoleByCode(approval.relatedObjectNo())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "审批角色不存在或已停用"));
            iamRepository.grantRole(idGenerator.nextId(), approval.applicantUserId(), role.id(), approver.userId());
        }

        auditLogService.writeSuccess(
            approver,
            "IAM",
            "APPROVED".equals(status) ? "ROLE_APPLICATION_APPROVE" : "ROLE_APPLICATION_REJECT",
            "ROLE_APPLICATION",
            approval.id(),
            approval.approvalNo(),
            null,
            "{\"status\":\"" + status + "\",\"role_code\":\"" + approval.relatedObjectNo() + "\"}");
        return toResponse(finished);
    }

    private ApprovalResponse toResponse(ApprovalRecord approval) {
        return new ApprovalResponse(
            approval.id(),
            approval.approvalNo(),
            approval.approvalType(),
            approval.title(),
            approval.relatedObjectNo(),
            approval.status(),
            approval.submittedAt(),
            approval.finishedAt());
    }

    public record RoleApplicationCommand(String roleCode, String submitReason) {
    }

    public record ApprovalActionCommand(String action, String approvalComment) {
    }

    public record ApprovalResponse(
        long approvalId,
        String approvalNo,
        String approvalType,
        String title,
        String roleCode,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime finishedAt
    ) {
    }
}
