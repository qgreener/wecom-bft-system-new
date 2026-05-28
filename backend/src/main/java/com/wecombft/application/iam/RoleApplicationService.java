package com.wecombft.application.iam;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.notification.NotificationContent;
import com.wecombft.application.notification.NotificationDispatchService;
import com.wecombft.infrastructure.integration.wecom.WecomApprovalAdapter;
import com.wecombft.infrastructure.integration.wecom.WecomApprovalCommand;
import com.wecombft.infrastructure.integration.wecom.WecomApprovalResult;
import com.wecombft.infrastructure.persistence.iam.ApprovalRecord;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.persistence.iam.RoleRecord;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

import com.wecombft.application.command.iam.ApprovalActionCommand;
import com.wecombft.application.command.iam.RoleApplicationCommand;
import com.wecombft.interfaces.dto.iam.ApprovalResponse;
@Service
public class RoleApplicationService {

    private final AdminSessionService adminSessionService;
    private final IamRepository iamRepository;
    private final AuditLogService auditLogService;
    private final IdGenerator idGenerator;
    private final NotificationDispatchService notificationDispatchService;
    private final WecomApprovalAdapter wecomApprovalAdapter;

    public RoleApplicationService(
        AdminSessionService adminSessionService,
        IamRepository iamRepository,
        AuditLogService auditLogService,
        IdGenerator idGenerator,
        NotificationDispatchService notificationDispatchService,
        WecomApprovalAdapter wecomApprovalAdapter
    ) {
        this.adminSessionService = adminSessionService;
        this.iamRepository = iamRepository;
        this.auditLogService = auditLogService;
        this.idGenerator = idGenerator;
        this.notificationDispatchService = notificationDispatchService;
        this.wecomApprovalAdapter = wecomApprovalAdapter;
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
        createRoleApplicationNotifications(principal, approval, role);
        submitWecomApproval(principal, approval, role);

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

    /** 当前未分配角色用户查"我有没有 PENDING 中的角色申请"。null = 没有。*/
    public ApprovalResponse findMyPending(String authorizationHeader) {
        AdminPrincipal principal = adminSessionService.require(authorizationHeader);
        return iamRepository.findAnyPendingRoleApplication(principal.userId())
            .map(this::toResponse)
            .orElse(null);
    }

    @Transactional
    public ApprovalResponse action(String authorizationHeader, long approvalId, ApprovalActionCommand command) {
        AdminPrincipal approver = adminSessionService.requirePermission(authorizationHeader, "iam:role-application:approve");
        ApprovalRecord approval = iamRepository.findApprovalById(approvalId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "审批记录不存在"));
        if (!"PENDING".equals(approval.status())) {
            auditLogService.writeFailure(
                approver,
                "SECURITY",
                "STATE_CONFLICT",
                "ROLE_APPLICATION",
                approval.id(),
                approval.approvalNo(),
                null,
                "审批记录不是待审批状态：" + approval.status());
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
        notifyApplicantOfResult(finished, status, command.approvalComment());

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

    private void createRoleApplicationNotifications(
        AdminPrincipal applicant,
        ApprovalRecord approval,
        RoleRecord role
    ) {
        NotificationContent content = new NotificationContent(
            "ROLE_APPLICATION",
            "ROLE_APPLICATION_MINIMAL",
            "角色申请待审批",
            applicant.displayName() + " 申请角色 " + role.roleName(),
            "ROLE_APPLICATION",
            approval.id(),
            "ROLE_APPLICATION:" + approval.id(),
            applicant.userId(),
            "https://finhub.tax/admin/#/approvals");
        for (Long approverUserId : iamRepository.findActiveUserIdsByRoleCode("SUPER_ADMIN")) {
            notificationDispatchService.dispatchToInternalUser(approverUserId, content);
        }
    }

    private void notifyApplicantOfResult(ApprovalRecord approval, String status, String comment) {
        String title = "APPROVED".equals(status) ? "角色申请已通过" : "角色申请被驳回";
        String body = "你的角色申请「" + approval.relatedObjectNo() + "」"
            + ("APPROVED".equals(status) ? "已通过" : "被驳回")
            + (comment == null || comment.isBlank() ? "" : "，备注：" + comment);
        NotificationContent content = new NotificationContent(
            "ROLE_APPLICATION_RESULT",
            "ROLE_APPLICATION_RESULT_MINIMAL",
            title,
            body,
            "ROLE_APPLICATION",
            approval.id(),
            "ROLE_APPLICATION_RESULT:" + approval.id() + ":" + status,
            approval.applicantUserId(),
            "https://finhub.tax/admin/#/dashboard");
        notificationDispatchService.dispatchToInternalUser(approval.applicantUserId(), content);
    }

    private void submitWecomApproval(AdminPrincipal applicant, ApprovalRecord approval, RoleRecord role) {
        try {
            String applicantWecom = iamRepository.findWecomUserIdByUserId(applicant.userId()).orElse(null);
            if (applicantWecom == null) {
                return;
            }
            java.util.List<String> approvers = iamRepository.findActiveUserIdsByRoleCode("SUPER_ADMIN")
                .stream()
                .map(uid -> iamRepository.findWecomUserIdByUserId(uid).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
            WecomApprovalResult result = wecomApprovalAdapter.createApproval(new WecomApprovalCommand(
                applicantWecom,
                "角色申请：" + role.roleName(),
                approval.submitReason() == null ? "(无说明)" : approval.submitReason(),
                approvers.isEmpty() ? null : approvers));
            if (result != null && result.success() && result.spNo() != null) {
                iamRepository.setApprovalWecomId(approval.id(), result.spNo());
            }
        } catch (RuntimeException e) {
            // 企微 OA 失败不影响本地审批单创建（doc 07 §4.2 一致：外部失败不回滚主业务）
        }
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



}
