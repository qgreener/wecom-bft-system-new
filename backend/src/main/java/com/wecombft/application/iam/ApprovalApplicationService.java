package com.wecombft.application.iam;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.command.iam.ApprovalActionCommand;
import com.wecombft.application.command.course.CourseApprovalActionCommand;
import com.wecombft.application.course.CourseApplicationService;
import com.wecombft.application.purchase.PurchaseApplicationService;
import com.wecombft.infrastructure.persistence.iam.ApprovalRecord;
import com.wecombft.infrastructure.persistence.iam.IamRepository;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;

/**
 * 审批回写统一入口（企微回调专用）。
 *
 * 企微「接收消息」推送 sys_approval_change 事件后，WecomCallbackController 调本服务，
 * 服务按 wecom_approval_id 查 approval_record，再按 approval_type 分发到对应业务 service，
 * 复用现有审批 action 路径（grantRole / publish / 推送供货商 等副作用一并发生）。
 *
 * 幂等：approval.status() 已是终态时直接返回，企微重试推送应静默 OK。
 */
@Service
public class ApprovalApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalApplicationService.class);

    private final IamRepository iamRepository;
    private final AdminSessionService adminSessionService;
    private final RoleApplicationService roleApplicationService;
    private final CourseApplicationService courseApplicationService;
    private final PurchaseApplicationService purchaseApplicationService;

    public ApprovalApplicationService(
        IamRepository iamRepository,
        AdminSessionService adminSessionService,
        RoleApplicationService roleApplicationService,
        CourseApplicationService courseApplicationService,
        PurchaseApplicationService purchaseApplicationService
    ) {
        this.iamRepository = iamRepository;
        this.adminSessionService = adminSessionService;
        this.roleApplicationService = roleApplicationService;
        this.courseApplicationService = courseApplicationService;
        this.purchaseApplicationService = purchaseApplicationService;
    }

    @Transactional
    public void applyWecomResult(String wecomApprovalId, String wecomStatus, String comment) {
        if (wecomApprovalId == null || wecomApprovalId.isBlank()) {
            log.warn("applyWecomResult skipped: empty sp_no");
            return;
        }
        Optional<ApprovalRecord> approvalOpt = iamRepository.findApprovalByWecomApprovalId(wecomApprovalId);
        if (approvalOpt.isEmpty()) {
            log.warn("applyWecomResult skipped: no approval_record for sp_no={}", wecomApprovalId);
            return;
        }
        ApprovalRecord approval = approvalOpt.get();
        if (!"PENDING".equals(approval.status())) {
            log.info("applyWecomResult skipped (already finalized): approvalId={} currentStatus={} sp_no={}",
                approval.id(), approval.status(), wecomApprovalId);
            return;
        }
        String action = mapAction(wecomStatus);
        if (action == null) {
            log.info("applyWecomResult skipped (non-terminal wecom status): sp_no={} wecomStatus={}",
                wecomApprovalId, wecomStatus);
            return;
        }
        dispatch(approval, action, comment);
    }

    private void dispatch(ApprovalRecord approval, String action, String comment) {
        String approvalType = approval.approvalType();
        if (approvalType == null) {
            log.warn("applyWecomResult: missing approval_type approvalId={}", approval.id());
            return;
        }
        switch (approvalType) {
            case "ROLE_APPLICATION" -> dispatchRoleApplication(approval, action, comment);
            case "PURCHASE_LARGE", "PURCHASE_APPROVAL" -> dispatchPurchase(approval, action, comment);
            case "COURSE_PUBLISH", "COURSE_OFFLINE", "COURSE_DELETE", "COURSE_APPROVAL" ->
                dispatchCourse(approval, action, comment);
            default -> log.warn("applyWecomResult: unknown approval_type={} approvalId={}",
                approvalType, approval.id());
        }
    }

    private void dispatchRoleApplication(ApprovalRecord approval, String action, String comment) {
        String token = systemSuperAdminToken();
        if (token == null) {
            log.warn("applyWecomResult: no SUPER_ADMIN available to act as system approver");
            return;
        }
        roleApplicationService.action("Bearer " + token, approval.id(),
            new ApprovalActionCommand(action, comment));
    }

    private void dispatchPurchase(ApprovalRecord approval, String action, String comment) {
        String token = systemSuperAdminToken();
        if (token == null) {
            log.warn("applyWecomResult: no SUPER_ADMIN available for purchase");
            return;
        }
        AdminPrincipal principal = adminSessionService.requirePermission(
            "Bearer " + token, "purchase:approval:approve");
        purchaseApplicationService.approvePurchase(principal, approval.id(),
            new com.wecombft.application.command.purchase.ApprovalActionCommand(action, comment));
    }

    private void dispatchCourse(ApprovalRecord approval, String action, String comment) {
        courseApplicationService.approvalAction(approval.id(),
            new CourseApprovalActionCommand(action, comment));
    }

    private String mapAction(String wecomStatus) {
        if (wecomStatus == null) return null;
        return switch (wecomStatus.trim()) {
            case "2", "APPROVED" -> "APPROVE";
            case "3", "REJECTED" -> "REJECT";
            default -> null;
        };
    }

    private String systemSuperAdminToken() {
        return iamRepository.findActiveUserIdsByRoleCode("SUPER_ADMIN").stream()
            .findFirst()
            .flatMap(iamRepository::findUserNoById)
            .map(adminSessionService::issueTestToken)
            .orElse(null);
    }
}
