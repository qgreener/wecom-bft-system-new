package com.wecombft.application.finance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.learning.LearningEntitlementService;
import com.wecombft.application.command.learning.RefundEntitlementCommand;
import com.wecombft.application.student.AppStudentApplicationService;
import com.wecombft.application.student.StudentSession;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRecord;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository.CallbackEventCommand;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository.DocumentLinkCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

import com.wecombft.application.CreationResult;
import com.wecombft.application.command.finance.AccountingMaterialCloseCommand;
import com.wecombft.application.command.finance.AccountingMaterialConfirmCommand;
import com.wecombft.application.command.finance.AccountingMaterialCreateCommand;
import com.wecombft.application.command.finance.AccountingMaterialUploadCommand;
import com.wecombft.application.command.finance.CompensationActionCommand;
import com.wecombft.application.command.finance.ReconciliationCheckCommand;
import com.wecombft.application.command.finance.RefundRetryCommand;
import com.wecombft.application.command.finance.InvoiceApplyCommand;
import com.wecombft.application.command.finance.InvoiceIssueCallbackCommand;
import com.wecombft.application.command.finance.InvoiceIssueCommand;
import com.wecombft.application.command.finance.InvoiceRedReverseCallbackCommand;
import com.wecombft.application.command.finance.InvoiceRedReverseCommand;
import com.wecombft.application.command.finance.InvoiceTitleCommand;
import com.wecombft.application.command.finance.ReconciliationImportCommand;
import com.wecombft.application.command.finance.ReconciliationRecordCommand;
import com.wecombft.application.command.finance.RefundApplyCommand;
import com.wecombft.application.command.finance.RefundApproveCommand;
import com.wecombft.application.command.finance.RefundCallbackCommand;
import com.wecombft.application.command.finance.RefundManualCompleteCommand;
import com.wecombft.application.command.finance.RefundRejectCommand;
import com.wecombft.interfaces.dto.finance.AccountingMaterialDownloadResponse;
import com.wecombft.interfaces.dto.finance.AccountingMaterialPage;
import com.wecombft.interfaces.dto.finance.AccountingMaterialResponse;
import com.wecombft.interfaces.dto.finance.AccountingWorkbenchSummaryResponse;
import com.wecombft.interfaces.dto.finance.CompensationRetryResponse;
import com.wecombft.interfaces.dto.finance.InvoiceCallbackResponse;
import com.wecombft.interfaces.dto.finance.InvoicePage;
import com.wecombft.interfaces.dto.finance.InvoiceResponse;
import com.wecombft.interfaces.dto.finance.InvoiceTitlePage;
import com.wecombft.interfaces.dto.finance.InvoiceTitleResponse;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchPage;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchResponse;
import com.wecombft.interfaces.dto.finance.ReconciliationRecordResponse;
import com.wecombft.interfaces.dto.finance.RefundCallbackResponse;
import com.wecombft.interfaces.dto.finance.RefundPage;
import com.wecombft.interfaces.dto.finance.RefundResponse;
@Service
public class AfterSalesFinanceApplicationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AppStudentApplicationService appStudentApplicationService;
    private final LearningEntitlementService learningEntitlementService;
    private final CallbackEventRepository callbackEventRepository;
    private final OrderDocumentLinkRepository documentLinkRepository;
    private final AuditLogService auditLogService;
    private final com.wecombft.application.notification.NotificationDispatchService notificationDispatchService;
    private final com.wecombft.infrastructure.persistence.iam.IamRepository iamRepository;
    private final com.wecombft.infrastructure.integration.mail.MailService mailService;

    public AfterSalesFinanceApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        ObjectMapper objectMapper,
        AppStudentApplicationService appStudentApplicationService,
        LearningEntitlementService learningEntitlementService,
        CallbackEventRepository callbackEventRepository,
        OrderDocumentLinkRepository documentLinkRepository,
        AuditLogService auditLogService,
        com.wecombft.application.notification.NotificationDispatchService notificationDispatchService,
        com.wecombft.infrastructure.persistence.iam.IamRepository iamRepository,
        com.wecombft.infrastructure.integration.mail.MailService mailService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.appStudentApplicationService = appStudentApplicationService;
        this.learningEntitlementService = learningEntitlementService;
        this.callbackEventRepository = callbackEventRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.auditLogService = auditLogService;
        this.notificationDispatchService = notificationDispatchService;
        this.iamRepository = iamRepository;
        this.mailService = mailService;
    }

    @Transactional
    public CreationResult<RefundResponse> applyRefund(String authorizationHeader, String idempotencyKey, RefundApplyCommand command) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<RefundRow> existing = findRefundByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new CreationResult<>(toRefundResponse(existing.get()), false);
        }

        long orderId = positive(command == null ? null : command.orderId(), "订单不能为空");
        OrderRow order = requireStudentOrder(orderId, student.studentId());
        if (!"PAID".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "未支付订单不能申请退款");
        }
        if (List.of("REVIEWING", "PROCESSING", "MANUAL_REQUIRED", "REFUNDED").contains(order.refundStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "订单已有退款处理中或已完成");
        }
        long amountCent = positive(command.applyAmountCent(), "退款金额必须大于 0");
        long paidAmountCent = order.paidAmountCent() == null ? order.payableAmountCent() : order.paidAmountCent();
        if (amountCent > paidAmountCent) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "退款金额不能超过实付金额");
        }
        PaymentRow payment = requireSuccessPayment(order.id());
        String entitlementAction = normalizeChoice(defaultString(command.entitlementAction(), "FREEZE"), List.of("FREEZE", "REVOKE"), "权益动作非法");

        long refundId = idGenerator.nextId();
        String refundNo = "REF" + refundId;
        try {
            jdbcTemplate.update(
                """
                insert into pay_refund (
                    id, refund_no, order_id, order_no, payment_id, student_id,
                    apply_amount_cent, refund_reason, apply_description, status,
                    entitlement_action, idempotency_key, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'REVIEWING', ?, ?, ?, ?)
                """,
                refundId,
                refundNo,
                order.id(),
                order.orderNo(),
                payment.paymentId(),
                student.studentId(),
                amountCent,
                requireText(command.refundReason(), "退款原因不能为空"),
                blankToNull(command.applyDescription()),
                entitlementAction,
                key,
                student.userId(),
                student.userId());
            insertRefundItems(refundId, refundNo, order, amountCent, student.userId());
        } catch (DuplicateKeyException duplicateKeyException) {
            return new CreationResult<>(toRefundResponse(findRefundByIdempotencyKey(key).orElseThrow()), false);
        }
        jdbcTemplate.update(
            """
            update trade_order
            set refund_status = 'REVIEWING',
                updated_by = ?,
                version = version + 1
            where id = ? and refund_status in ('NONE', 'REJECTED', 'FAILED')
            """,
            student.userId(),
            order.id());
        auditLogService.writeSystemSuccess(
            "REFUND",
            "REFUND_APPLY",
            "PAY_REFUND",
            refundId,
            refundNo,
            order.id(),
            "{\"status\":\"REVIEWING\",\"apply_amount_cent\":" + amountCent + "}");
        return new CreationResult<>(toRefundResponse(requireRefund(refundId)), true);
    }

    public RefundPage appRefunds(String authorizationHeader) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        return new RefundPage(jdbcTemplate.query(
            """
            select id, refund_no, order_id, order_no, payment_id, student_id, apply_amount_cent,
                   approved_amount_cent, refund_reason, apply_description, status, reviewer_user_id,
                   review_comment, reject_reason, refund_channel, external_refund_no,
                   manual_voucher_no, manual_voucher_file, failure_reason, refunded_at,
                   entitlement_action, created_at
            from pay_refund
            where student_id = ?
            order by created_at desc, id desc
            """,
            this::mapRefund,
            student.studentId()).stream().map(this::toRefundResponse).toList());
    }

    public RefundPage adminRefunds(AdminPrincipal principal, String status, String orderNo) {
        requireAdmin(principal);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            select id, refund_no, order_id, order_no, payment_id, student_id, apply_amount_cent,
                   approved_amount_cent, refund_reason, apply_description, status, reviewer_user_id,
                   review_comment, reject_reason, refund_channel, external_refund_no,
                   manual_voucher_no, manual_voucher_file, failure_reason, refunded_at,
                   entitlement_action, created_at
            from pay_refund
            where 1 = 1
            """);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (orderNo != null && !orderNo.isBlank()) {
            sql.append(" and order_no = ?");
            args.add(orderNo.trim());
        }
        sql.append(" order by created_at desc, id desc");
        return new RefundPage(jdbcTemplate.query(sql.toString(), this::mapRefund, args.toArray()).stream()
            .map(this::toRefundResponse)
            .toList());
    }

    public RefundResponse adminRefundDetail(AdminPrincipal principal, long refundId) {
        requireAdmin(principal);
        return toRefundResponse(requireRefund(refundId));
    }

    public RefundResponse appRefundDetail(String authorizationHeader, long refundId) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        RefundRow refund = requireRefund(refundId);
        if (refund.studentId() != student.studentId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该退款单");
        }
        return toRefundResponse(refund);
    }

    @Transactional
    public RefundResponse rejectRefund(AdminPrincipal principal, long refundId, RefundRejectCommand command) {
        requireAdmin(principal);
        RefundRow refund = requireRefund(refundId);
        if ("REJECTED".equals(refund.status())) {
            return toRefundResponse(refund);
        }
        if (!"REVIEWING".equals(refund.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "当前状态不能拒绝退款");
        }
        jdbcTemplate.update(
            """
            update pay_refund
            set status = 'REJECTED',
                reviewer_user_id = ?,
                reject_reason = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'REVIEWING'
            """,
            principal.userId(),
            requireText(command == null ? null : command.rejectReason(), "拒绝原因不能为空"),
            principal.userId(),
            refundId);
        jdbcTemplate.update(
            """
            update trade_order
            set refund_status = 'REJECTED',
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            principal.userId(),
            refund.orderId());
        auditLogService.writeSuccess(principal, "REFUND", "REFUND_REJECT", "PAY_REFUND", refund.id(), refund.refundNo(), refund.orderId(), "{\"status\":\"REJECTED\"}");
        return toRefundResponse(requireRefund(refundId));
    }

    @Transactional
    public RefundResponse approveRefund(AdminPrincipal principal, String idempotencyKey, long refundId, RefundApproveCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        RefundRow refund = requireRefund(refundId);
        if ("PROCESSING".equals(refund.status()) || "MANUAL_REQUIRED".equals(refund.status()) || "REFUNDED".equals(refund.status())) {
            return toRefundResponse(refund);
        }
        if (!"REVIEWING".equals(refund.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "当前状态不能审核通过退款");
        }
        long approvedAmount = positive(command == null ? null : command.approvedAmountCent(), "通过金额必须大于 0");
        if (approvedAmount > refund.applyAmountCent()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "通过金额不能超过申请金额");
        }
        String channel = normalizeChoice(command.refundChannel(), List.of("ORIGINAL", "MANUAL"), "退款渠道非法");
        String nextStatus = "ORIGINAL".equals(channel) ? "PROCESSING" : "MANUAL_REQUIRED";
        String entitlementAction = normalizeChoice(defaultString(command.entitlementAction(), defaultString(refund.entitlementAction(), "FREEZE")), List.of("FREEZE", "REVOKE"), "权益动作非法");
        jdbcTemplate.update(
            """
            update pay_refund
            set status = ?,
                approved_amount_cent = ?,
                reviewer_user_id = ?,
                review_comment = ?,
                refund_channel = ?,
                entitlement_action = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'REVIEWING'
            """,
            nextStatus,
            approvedAmount,
            principal.userId(),
            blankToNull(command.reviewComment()),
            channel,
            entitlementAction,
            principal.userId(),
            refundId);
        jdbcTemplate.update(
            """
            update trade_order
            set refund_status = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and refund_status = 'REVIEWING'
            """,
            nextStatus,
            principal.userId(),
            refund.orderId());
        auditLogService.writeSuccess(principal, "REFUND", "REFUND_APPROVE", "PAY_REFUND", refund.id(), refund.refundNo(), refund.orderId(), "{\"status\":\"" + nextStatus + "\",\"channel\":\"" + channel + "\"}");
        notifyAccountingOfRefundApproved(principal, refund, nextStatus, channel);
        return toRefundResponse(requireRefund(refundId));
    }

    private void notifyAccountingOfRefundApproved(
        AdminPrincipal approver, RefundRow refund, String nextStatus, String channel
    ) {
        com.wecombft.application.notification.NotificationContent content =
            new com.wecombft.application.notification.NotificationContent(
                "REFUND_APPROVED",
                "REFUND_APPROVED_NOTICE",
                "退款已审批通过，请确认财务",
                "订单 " + refund.orderId() + " 退款单 " + refund.refundNo()
                    + " 已审批通过，下一步：" + nextStatus + "，通道：" + channel,
                "PAY_REFUND",
                refund.id(),
                "REFUND_APPROVED:" + refund.id(),
                approver.userId(),
                "https://finhub.tax/admin/#/refunds");
        for (Long accountingUserId : iamRepository.findActiveUserIdsByRoleCode("ACCOUNTING")) {
            notificationDispatchService.dispatchToInternalUser(accountingUserId, content);
        }
    }

    @Transactional
    public RefundResponse manualCompleteRefund(AdminPrincipal principal, String idempotencyKey, long refundId, RefundManualCompleteCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        RefundRow refund = requireRefund(refundId);
        if ("REFUNDED".equals(refund.status())) {
            return toRefundResponse(refund);
        }
        if (!List.of("MANUAL_REQUIRED", "FAILED", "PROCESSING").contains(refund.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "当前状态不能人工完成退款");
        }
        String entitlementAction = normalizeChoice(defaultString(command == null ? null : command.entitlementAction(), defaultString(refund.entitlementAction(), "FREEZE")), List.of("FREEZE", "REVOKE"), "权益动作非法");
        LocalDateTime refundedAt = command == null || command.refundedAt() == null ? LocalDateTime.now() : command.refundedAt();
        jdbcTemplate.update(
            """
            update pay_refund
            set status = 'REFUNDED',
                refund_channel = 'MANUAL',
                manual_voucher_no = ?,
                manual_voucher_file = ?,
                refunded_at = ?,
                failure_reason = null,
                entitlement_action = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status <> 'REFUNDED'
            """,
            requireText(command == null ? null : command.manualVoucherNo(), "人工退款凭证号不能为空"),
            requireText(command.manualVoucherFile(), "人工退款凭证文件不能为空"),
            refundedAt,
            entitlementAction,
            principal.userId(),
            refundId);
        runRefundSuccessSideEffects(requireRefund(refundId), refundedAt, principal.userId());
        auditLogService.writeSuccess(principal, "REFUND", "REFUND_MANUAL_COMPLETE", "PAY_REFUND", refund.id(), refund.refundNo(), refund.orderId(), "{\"status\":\"REFUNDED\"}");
        return toRefundResponse(requireRefund(refundId));
    }

    @Transactional
    public RefundResponse retryRefund(AdminPrincipal principal, String idempotencyKey, long refundId, RefundRetryCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        RefundRow refund = requireRefund(refundId);
        if ("PROCESSING".equals(refund.status()) || "MANUAL_REQUIRED".equals(refund.status()) || "REFUNDED".equals(refund.status())) {
            return toRefundResponse(refund);
        }
        if (!"FAILED".equals(refund.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "仅退款失败可重试");
        }
        String retryMode = normalizeChoice(
            command == null ? null : command.retryMode(),
            List.of("ORIGINAL", "MANUAL_REQUIRED"),
            "重试模式非法");
        String nextStatus = "ORIGINAL".equals(retryMode) ? "PROCESSING" : "MANUAL_REQUIRED";
        String entitlementAction = defaultString(refund.entitlementAction(), "FREEZE");
        jdbcTemplate.update(
            """
            update pay_refund
            set status = ?,
                refund_channel = ?,
                failure_reason = null,
                entitlement_action = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'FAILED'
            """,
            nextStatus,
            "ORIGINAL".equals(retryMode) ? "ORIGINAL" : "MANUAL",
            entitlementAction,
            principal.userId(),
            refundId);
        jdbcTemplate.update(
            """
            update trade_order
            set refund_status = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and refund_status = 'FAILED'
            """,
            nextStatus,
            principal.userId(),
            refund.orderId());
        auditLogService.writeSuccess(
            principal,
            "REFUND",
            "REFUND_RETRY",
            "PAY_REFUND",
            refund.id(),
            refund.refundNo(),
            refund.orderId(),
            "{\"status\":\"" + nextStatus + "\",\"retry_mode\":\"" + retryMode + "\"}");
        return toRefundResponse(requireRefund(refundId));
    }

    @Transactional
    public RefundCallbackResponse handleWechatRefundCallback(RefundCallbackCommand command) {
        validateRefundCallback(command);
        String refundNo = command.refundNo().trim();
        Optional<RefundRow> refundOptional = findRefundByNo(refundNo);
        RefundRow refundForEvent = refundOptional.orElse(null);
        String idempotencyKey = "REFUND:" + refundNo + ":" + defaultString(command.externalRefundNo(), command.eventNo());
        String rawSnapshot = toJson(command.rawSnapshot() == null ? Map.of("event_no", command.eventNo()) : command.rawSnapshot());
        CallbackEventRecord callbackEvent = callbackEventRepository.recordReceived(new CallbackEventCommand(
            idGenerator.nextId(),
            command.eventNo().trim(),
            "WECHAT_REFUND",
            "REFUND_RESULT",
            idempotencyKey,
            refundForEvent == null ? null : refundForEvent.orderId(),
            "REFUND",
            refundForEvent == null ? null : refundForEvent.id(),
            refundNo,
            rawSnapshot));
        if (!"PENDING".equals(callbackEvent.processingStatus())) {
            return refundCallbackResponse(callbackEvent, findRefundByNo(refundNo).orElse(null));
        }
        if (refundOptional.isEmpty()) {
            callbackEventRepository.markFailed(callbackEvent.id(), "退款单不存在");
            return refundCallbackResponse(callbackEventRepository.findBySourceAndEventNo("WECHAT_REFUND", command.eventNo()).orElseThrow(), null);
        }

        RefundRow refund = refundOptional.get();
        if ("REFUNDED".equals(refund.status())) {
            callbackEventRepository.markProcessed(callbackEvent.id());
            return refundCallbackResponse(callbackEventRepository.findBySourceAndEventNo("WECHAT_REFUND", command.eventNo()).orElseThrow(), requireRefund(refund.id()));
        }
        String callbackStatus = normalizeText(command.refundStatus());
        if ("SUCCESS".equals(callbackStatus)) {
            LocalDateTime refundedAt = command.refundedAt() == null ? LocalDateTime.now() : command.refundedAt();
            jdbcTemplate.update(
                """
                update pay_refund
                set status = 'REFUNDED',
                    external_refund_no = ?,
                    refunded_at = ?,
                    approved_amount_cent = coalesce(approved_amount_cent, ?),
                    failure_reason = null,
                    updated_by = 0,
                    version = version + 1
                where id = ? and status <> 'REFUNDED'
                """,
                blankToNull(command.externalRefundNo()),
                refundedAt,
                command.refundedAmountCent(),
                refund.id());
            runRefundSuccessSideEffects(requireRefund(refund.id()), refundedAt, null);
            callbackEventRepository.markProcessed(callbackEvent.id());
            return refundCallbackResponse(callbackEventRepository.findBySourceAndEventNo("WECHAT_REFUND", command.eventNo()).orElseThrow(), requireRefund(refund.id()));
        }
        jdbcTemplate.update(
            """
            update pay_refund
            set status = 'FAILED',
                external_refund_no = ?,
                failure_reason = ?,
                updated_by = 0,
                version = version + 1
            where id = ? and status not in ('REFUNDED', 'REJECTED')
            """,
            blankToNull(command.externalRefundNo()),
            defaultString(command.failureReason(), "退款回调失败"),
            refund.id());
        jdbcTemplate.update(
            """
            update trade_order
            set refund_status = 'FAILED',
                updated_by = 0,
                version = version + 1
            where id = ? and refund_status <> 'REFUNDED'
            """,
            refund.orderId());
        callbackEventRepository.markFailed(callbackEvent.id(), defaultString(command.failureReason(), "退款回调失败"));
        auditLogService.writeSystemFailure("REFUND", "REFUND_CALLBACK_FAILED", "PAY_REFUND", refund.id(), refund.refundNo(), refund.orderId(), defaultString(command.failureReason(), "退款回调失败"));
        return refundCallbackResponse(callbackEventRepository.findBySourceAndEventNo("WECHAT_REFUND", command.eventNo()).orElseThrow(), requireRefund(refund.id()));
    }

    @Transactional
    public CreationResult<InvoiceTitleResponse> saveInvoiceTitle(String authorizationHeader, String idempotencyKey, InvoiceTitleCommand command) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<InvoiceTitleRow> existing = findInvoiceTitleByIdempotency(student.studentId(), key);
        if (existing.isPresent()) {
            return new CreationResult<>(toInvoiceTitleResponse(existing.get()), false);
        }
        long titleId = idGenerator.nextId();
        if (Boolean.TRUE.equals(command == null ? null : command.isDefault())) {
            jdbcTemplate.update("update student_invoice_title set is_default = 0, updated_by = ? where student_id = ? and deleted_flag = 0", student.userId(), student.studentId());
        }
        jdbcTemplate.update(
            """
            insert into student_invoice_title (
                id, student_id, title_type, title_name, tax_no, email, is_default,
                status, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
            """,
            titleId,
            student.studentId(),
            normalizeChoice(command.titleType(), List.of("PERSONAL", "COMPANY"), "抬头类型非法"),
            requireText(command.titleName(), "发票抬头不能为空"),
            blankToNull(command.taxNo()),
            blankToNull(command.email()),
            Boolean.TRUE.equals(command.isDefault()) ? 1 : 0,
            key,
            student.userId(),
            student.userId());
        auditLogService.writeSystemSuccess("INVOICE", "INVOICE_TITLE_SAVE", "STUDENT_INVOICE_TITLE", titleId, "TITLE" + titleId, null, "{\"student_id\":" + student.studentId() + "}");
        return new CreationResult<>(toInvoiceTitleResponse(requireInvoiceTitle(titleId)), true);
    }

    public InvoiceTitlePage invoiceTitles(String authorizationHeader) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        return new InvoiceTitlePage(jdbcTemplate.query(
            """
            select id, student_id, title_type, title_name, tax_no, email, is_default, status
            from student_invoice_title
            where student_id = ? and deleted_flag = 0
            order by is_default desc, updated_at desc, id desc
            """,
            this::mapInvoiceTitle,
            student.studentId()).stream().map(this::toInvoiceTitleResponse).toList());
    }

    @Transactional
    public CreationResult<InvoiceResponse> applyInvoice(String authorizationHeader, String idempotencyKey, InvoiceApplyCommand command) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<InvoiceRow> existing = findInvoiceByIdempotency(student.studentId(), key);
        if (existing.isPresent()) {
            return new CreationResult<>(toInvoiceResponse(existing.get()), false);
        }
        OrderRow order = requireStudentOrder(positive(command == null ? null : command.orderId(), "订单不能为空"), student.studentId());
        if (!"PAID".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "未支付订单不能申请开票");
        }
        if (List.of("REVIEWING", "PROCESSING", "MANUAL_REQUIRED").contains(order.refundStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "退款中的订单不能申请开票");
        }
        if ("REFUNDED".equals(order.refundStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "已退款订单不能申请开票");
        }
        if (!"NOT_APPLIED".equals(order.invoiceStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "订单已申请开票");
        }
        InvoiceTitleRow title = requireStudentInvoiceTitle(command.titleId(), student.studentId());
        long invoiceId = idGenerator.nextId();
        String invoiceApplyNo = "INVAPP" + invoiceId;
        long amount = order.paidAmountCent() == null ? order.payableAmountCent() : order.paidAmountCent();
        jdbcTemplate.update(
            """
            insert into tax_invoice (
                id, invoice_apply_no, order_id, order_no, student_id, title_type, title_name,
                tax_no, email, invoice_amount_cent, tax_amount_cent, tax_rule_snapshot,
                status, invoice_channel, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'APPLIED', 'MOCK', ?, ?, ?)
            """,
            invoiceId,
            invoiceApplyNo,
            order.id(),
            order.orderNo(),
            student.studentId(),
            title.titleType(),
            title.titleName(),
            title.taxNo(),
            defaultString(command.email(), title.email()),
            amount,
            defaultString(order.taxSnapshotJson(), "{}"),
            key,
            student.userId(),
            student.userId());
        insertInvoiceItems(invoiceId, invoiceApplyNo, order, student.userId());
        jdbcTemplate.update(
            """
            update trade_order
            set invoice_status = 'APPLIED',
                updated_by = ?,
                version = version + 1
            where id = ? and invoice_status = 'NOT_APPLIED'
            """,
            student.userId(),
            order.id());
        upsertDocumentLink(order, "INVOICE", invoiceId, invoiceApplyNo, "APPLIED", amount, "INVOICE_APPLY", "tax_invoice", "申请开票", student.userId());
        auditLogService.writeSystemSuccess("INVOICE", "INVOICE_APPLY", "TAX_INVOICE", invoiceId, invoiceApplyNo, order.id(), "{\"status\":\"APPLIED\",\"amount\":" + amount + "}");
        return new CreationResult<>(toInvoiceResponse(requireInvoice(invoiceId)), true);
    }

    public InvoicePage appInvoices(String authorizationHeader) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        return new InvoicePage(jdbcTemplate.query(
            """
            select id, invoice_apply_no, order_id, order_no, student_id, title_type, title_name,
                   tax_no, email, invoice_amount_cent, status, invoice_channel, invoice_no,
                   invoice_file, issued_at, source_refund_id, red_invoice_no, red_invoice_file,
                   red_reversed_at, failure_reason, created_at
            from tax_invoice
            where student_id = ?
            order by created_at desc, id desc
            """,
            this::mapInvoice,
            student.studentId()).stream().map(this::toInvoiceResponse).toList());
    }

    public InvoicePage adminInvoices(AdminPrincipal principal, String status, String orderNo) {
        requireAdmin(principal);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            select id, invoice_apply_no, order_id, order_no, student_id, title_type, title_name,
                   tax_no, email, invoice_amount_cent, status, invoice_channel, invoice_no,
                   invoice_file, issued_at, source_refund_id, red_invoice_no, red_invoice_file,
                   red_reversed_at, failure_reason, created_at
            from tax_invoice
            where 1 = 1
            """);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (orderNo != null && !orderNo.isBlank()) {
            sql.append(" and order_no = ?");
            args.add(orderNo.trim());
        }
        sql.append(" order by created_at desc, id desc");
        return new InvoicePage(jdbcTemplate.query(sql.toString(), this::mapInvoice, args.toArray()).stream()
            .map(this::toInvoiceResponse)
            .toList());
    }

    public InvoiceResponse adminInvoiceDetail(AdminPrincipal principal, long invoiceId) {
        requireAdmin(principal);
        return toInvoiceResponse(requireInvoice(invoiceId));
    }

    @Transactional
    public InvoiceResponse issueInvoice(AdminPrincipal principal, String idempotencyKey, long invoiceId, InvoiceIssueCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        InvoiceRow invoice = requireInvoice(invoiceId);
        if ("ISSUED".equals(invoice.status()) || "RED_REVERSED".equals(invoice.status())) {
            return toInvoiceResponse(invoice);
        }
        if (!List.of("APPLIED", "TO_BE_ISSUED").contains(invoice.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "当前状态不能开票");
        }
        LocalDateTime issuedAt = command == null || command.issuedAt() == null ? LocalDateTime.now() : command.issuedAt();
        jdbcTemplate.update(
            """
            update tax_invoice
            set status = 'ISSUED',
                invoice_channel = 'MANUAL',
                invoice_no = ?,
                invoice_file = ?,
                issued_at = ?,
                failure_reason = null,
                updated_by = ?,
                version = version + 1
            where id = ? and status in ('APPLIED', 'TO_BE_ISSUED')
            """,
            requireText(command == null ? null : command.invoiceNo(), "发票号码不能为空"),
            requireText(command.invoiceFile(), "发票文件不能为空"),
            issuedAt,
            principal.userId(),
            invoiceId);
        jdbcTemplate.update("update trade_order set invoice_status = 'ISSUED', updated_by = ?, version = version + 1 where id = ?", principal.userId(), invoice.orderId());
        OrderRow order = requireOrder(invoice.orderId());
        upsertDocumentLink(order, "INVOICE", invoiceId, command.invoiceNo(), "ISSUED", invoice.invoiceAmountCent(), "INVOICE_ISSUE", "tax_invoice", "人工开票", principal.userId());
        auditLogService.writeSuccess(principal, "INVOICE", "INVOICE_ISSUE", "TAX_INVOICE", invoice.id(), command.invoiceNo(), invoice.orderId(), "{\"status\":\"ISSUED\"}");
        sendInvoiceMailIfPossible(invoice, command.invoiceNo(), command.invoiceFile());
        return toInvoiceResponse(requireInvoice(invoiceId));
    }

    private void sendInvoiceMailIfPossible(InvoiceRow invoice, String invoiceNo, String invoiceFile) {
        if (invoice.email() == null || invoice.email().isBlank()) {
            return;
        }
        try {
            String subject = "您的发票已开具：" + invoice.titleName();
            String content = "尊敬的学员：\n\n您的订单（" + invoice.orderNo() + "）发票已开具。\n"
                + "发票号码：" + (invoiceNo == null ? "-" : invoiceNo) + "\n"
                + "发票文件号：" + (invoiceFile == null ? "-" : invoiceFile) + "\n"
                + "如需协助，请回复本邮件或联系客服。";
            mailService.send(invoice.email(), subject, content);
        } catch (Exception e) {
            // 邮件失败不影响开票主流程
        }
    }

    @Transactional
    public InvoiceResponse redReverseInvoice(AdminPrincipal principal, String idempotencyKey, long invoiceId, InvoiceRedReverseCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        InvoiceRow invoice = requireInvoice(invoiceId);
        if ("RED_REVERSED".equals(invoice.status())) {
            return toInvoiceResponse(invoice);
        }
        if (!"ISSUED".equals(invoice.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "未开票不能红冲");
        }
        LocalDateTime reversedAt = command == null || command.redReversedAt() == null ? LocalDateTime.now() : command.redReversedAt();
        jdbcTemplate.update(
            """
            update tax_invoice
            set status = 'RED_REVERSED',
                red_invoice_no = ?,
                red_invoice_file = ?,
                red_reversed_at = ?,
                failure_reason = null,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'ISSUED'
            """,
            requireText(command == null ? null : command.redInvoiceNo(), "红冲发票号码不能为空"),
            requireText(command.redInvoiceFile(), "红冲发票文件不能为空"),
            reversedAt,
            principal.userId(),
            invoiceId);
        jdbcTemplate.update("update trade_order set invoice_status = 'RED_REVERSED', updated_by = ?, version = version + 1 where id = ?", principal.userId(), invoice.orderId());
        upsertDocumentLink(requireOrder(invoice.orderId()), "RED_REVERSAL", invoiceId, command.redInvoiceNo(), "RED_REVERSED", invoice.invoiceAmountCent(), "INVOICE_RED_REVERSE", "tax_invoice", "发票红冲", principal.userId());
        auditLogService.writeSuccess(principal, "INVOICE", "INVOICE_RED_REVERSE", "TAX_INVOICE", invoice.id(), command.redInvoiceNo(), invoice.orderId(), "{\"status\":\"RED_REVERSED\"}");
        return toInvoiceResponse(requireInvoice(invoiceId));
    }

    @Transactional
    public InvoiceCallbackResponse handleInvoiceIssueCallback(InvoiceIssueCallbackCommand command) {
        validateInvoiceIssueCallback(command);
        Optional<InvoiceRow> invoiceOptional = findInvoiceByApplyNo(command.invoiceApplyNo());
        InvoiceRow invoiceForEvent = invoiceOptional.orElse(null);
        CallbackEventRecord callbackEvent = callbackEventRepository.recordReceived(new CallbackEventCommand(
            idGenerator.nextId(),
            command.eventNo().trim(),
            "INVOICE_MOCK",
            "INVOICE_ISSUE",
            "INVOICE_ISSUE:" + command.invoiceApplyNo() + ":" + command.eventNo(),
            invoiceForEvent == null ? null : invoiceForEvent.orderId(),
            "INVOICE",
            invoiceForEvent == null ? null : invoiceForEvent.id(),
            command.invoiceApplyNo(),
            toJson(command.rawSnapshot() == null ? Map.of("event_no", command.eventNo()) : command.rawSnapshot())));
        if (!"PENDING".equals(callbackEvent.processingStatus())) {
            return invoiceCallbackResponse(callbackEvent, invoiceForEvent);
        }
        if (invoiceOptional.isEmpty()) {
            callbackEventRepository.markFailed(callbackEvent.id(), "发票申请不存在");
            return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), null);
        }
        InvoiceRow invoice = invoiceOptional.get();
        if ("FAILED".equals(normalizeText(command.status()))) {
            jdbcTemplate.update(
                """
                update tax_invoice
                set status = 'TO_BE_ISSUED',
                    failure_reason = ?,
                    updated_by = 0,
                    version = version + 1
                where id = ? and status = 'APPLIED'
                """,
                defaultString(command.failureReason(), "自动开票失败"),
                invoice.id());
            jdbcTemplate.update("update trade_order set invoice_status = 'TO_BE_ISSUED', updated_by = 0, version = version + 1 where id = ?", invoice.orderId());
            callbackEventRepository.markFailed(callbackEvent.id(), defaultString(command.failureReason(), "自动开票失败"));
            auditLogService.writeSystemFailure("INVOICE", "INVOICE_MOCK_ISSUE_FAILED", "TAX_INVOICE", invoice.id(), invoice.invoiceApplyNo(), invoice.orderId(), defaultString(command.failureReason(), "自动开票失败"));
            return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), requireInvoice(invoice.id()));
        }
        jdbcTemplate.update(
            """
            update tax_invoice
            set status = 'ISSUED',
                invoice_channel = 'MOCK',
                invoice_no = ?,
                invoice_file = ?,
                issued_at = ?,
                failure_reason = null,
                updated_by = 0,
                version = version + 1
            where id = ? and status in ('APPLIED', 'TO_BE_ISSUED')
            """,
            requireText(command.invoiceNo(), "发票号码不能为空"),
            requireText(command.invoiceFile(), "发票文件不能为空"),
            command.issuedAt() == null ? LocalDateTime.now() : command.issuedAt(),
            invoice.id());
        jdbcTemplate.update("update trade_order set invoice_status = 'ISSUED', updated_by = 0, version = version + 1 where id = ?", invoice.orderId());
        callbackEventRepository.markProcessed(callbackEvent.id());
        auditLogService.writeSystemSuccess("INVOICE", "INVOICE_MOCK_ISSUE_SUCCESS", "TAX_INVOICE", invoice.id(), command.invoiceNo(), invoice.orderId(), "{\"status\":\"ISSUED\"}");
        return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), requireInvoice(invoice.id()));
    }

    @Transactional
    public InvoiceCallbackResponse handleInvoiceRedReverseCallback(InvoiceRedReverseCallbackCommand command) {
        if (command == null || command.eventNo() == null || command.eventNo().isBlank() || command.invoiceApplyNo() == null || command.invoiceApplyNo().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "红冲回调关键字段不能为空");
        }
        Optional<InvoiceRow> invoiceOptional = findInvoiceByApplyNo(command.invoiceApplyNo());
        InvoiceRow invoiceForEvent = invoiceOptional.orElse(null);
        CallbackEventRecord callbackEvent = callbackEventRepository.recordReceived(new CallbackEventCommand(
            idGenerator.nextId(),
            command.eventNo().trim(),
            "INVOICE_MOCK",
            "INVOICE_RED_REVERSE",
            "INVOICE_RED:" + command.invoiceApplyNo() + ":" + command.eventNo(),
            invoiceForEvent == null ? null : invoiceForEvent.orderId(),
            "INVOICE",
            invoiceForEvent == null ? null : invoiceForEvent.id(),
            command.invoiceApplyNo(),
            toJson(command.rawSnapshot() == null ? Map.of("event_no", command.eventNo()) : command.rawSnapshot())));
        if (invoiceOptional.isEmpty()) {
            callbackEventRepository.markFailed(callbackEvent.id(), "发票申请不存在");
            return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), null);
        }
        InvoiceRow invoice = invoiceOptional.get();
        if ("RED_REVERSED".equals(invoice.status())) {
            callbackEventRepository.markProcessed(callbackEvent.id());
            return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), invoice);
        }
        if ("FAILED".equals(normalizeText(command.status()))) {
            jdbcTemplate.update("update tax_invoice set failure_reason = ?, updated_by = 0, version = version + 1 where id = ?", defaultString(command.failureReason(), "红冲失败"), invoice.id());
            callbackEventRepository.markFailed(callbackEvent.id(), defaultString(command.failureReason(), "红冲失败"));
            auditLogService.writeSystemFailure("INVOICE", "INVOICE_RED_REVERSE_FAILED", "TAX_INVOICE", invoice.id(), invoice.invoiceApplyNo(), invoice.orderId(), defaultString(command.failureReason(), "红冲失败"));
            return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), requireInvoice(invoice.id()));
        }
        jdbcTemplate.update(
            """
            update tax_invoice
            set status = 'RED_REVERSED',
                red_invoice_no = ?,
                red_invoice_file = ?,
                red_reversed_at = ?,
                failure_reason = null,
                updated_by = 0,
                version = version + 1
            where id = ? and status = 'ISSUED'
            """,
            requireText(command.redInvoiceNo(), "红冲发票号码不能为空"),
            requireText(command.redInvoiceFile(), "红冲发票文件不能为空"),
            command.redReversedAt() == null ? LocalDateTime.now() : command.redReversedAt(),
            invoice.id());
        jdbcTemplate.update("update trade_order set invoice_status = 'RED_REVERSED', updated_by = 0, version = version + 1 where id = ?", invoice.orderId());
        callbackEventRepository.markProcessed(callbackEvent.id());
        auditLogService.writeSystemSuccess("INVOICE", "INVOICE_RED_REVERSE_SUCCESS", "TAX_INVOICE", invoice.id(), command.redInvoiceNo(), invoice.orderId(), "{\"status\":\"RED_REVERSED\"}");
        return invoiceCallbackResponse(callbackEventRepository.findBySourceAndEventNo("INVOICE_MOCK", command.eventNo()).orElseThrow(), requireInvoice(invoice.id()));
    }

    @Transactional
    public CreationResult<ReconciliationBatchResponse> importReconciliation(AdminPrincipal principal, String idempotencyKey, ReconciliationImportCommand command) {
        requireAdmin(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<ReconciliationBatchRow> existing = findReconciliationByIdempotency(key);
        if (existing.isPresent()) {
            return new CreationResult<>(toReconciliationBatchResponse(existing.get(), reconciliationRecords(existing.get().id())), false);
        }
        long batchId = idGenerator.nextId();
        String batchNo = "REC" + batchId;
        String billMonth = requireText(command == null ? null : command.billMonth(), "账单月份不能为空");
        String billSource = normalizeText(command.billSource());
        List<ReconciliationRecordCommand> records = command.records() == null ? List.of() : command.records();
        jdbcTemplate.update(
            """
            insert into finance_reconciliation_batch (
                id, batch_no, bill_month, bill_source, file_name, file_digest,
                import_status, total_count, matched_count, diff_count,
                imported_by, imported_at, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, 'IMPORTED', 0, 0, 0, ?, ?, ?, ?, ?)
            """,
            batchId,
            batchNo,
            billMonth,
            billSource,
            requireText(command.fileName(), "账单文件名不能为空"),
            requireText(command.fileDigest(), "账单文件摘要不能为空"),
            principal.userId(),
            LocalDateTime.now(),
            key,
            principal.userId(),
            principal.userId());

        int matched = 0;
        int diff = 0;
        Set<String> seen = new HashSet<>();
        for (ReconciliationRecordCommand record : records) {
            ReconciliationPreparedRecord prepared = prepareReconciliationRecord(billMonth, billSource, record, seen);
            if ("MATCHED".equals(prepared.result())) {
                matched++;
            } else {
                diff++;
            }
            insertReconciliationRecord(batchId, batchNo, billMonth, billSource, prepared, principal.userId());
        }
        jdbcTemplate.update(
            """
            update finance_reconciliation_batch
            set total_count = ?,
                matched_count = ?,
                diff_count = ?,
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            records.size(),
            matched,
            diff,
            principal.userId(),
            batchId);
        auditLogService.writeSuccess(principal, "FINANCE", "RECONCILIATION_IMPORT", "FINANCE_RECONCILIATION_BATCH", batchId, batchNo, null, "{\"total_count\":" + records.size() + ",\"diff_count\":" + diff + "}");
        return new CreationResult<>(toReconciliationBatchResponse(requireReconciliationBatch(batchId), reconciliationRecords(batchId)), true);
    }

    public ReconciliationBatchPage reconciliationBatches() {
        return new ReconciliationBatchPage(jdbcTemplate.query(
            """
            select id, batch_no, bill_month, bill_source, file_name, file_digest, import_status,
                   total_count, matched_count, diff_count, imported_by, imported_at, failure_reason
            from finance_reconciliation_batch
            order by imported_at desc, id desc
            """,
            this::mapReconciliationBatch).stream().map(row -> toReconciliationBatchResponse(row, List.of())).toList());
    }

    public ReconciliationBatchResponse reconciliationDetail(long batchId) {
        return toReconciliationBatchResponse(requireReconciliationBatch(batchId), reconciliationRecords(batchId));
    }

    @Transactional
    public ReconciliationRecordResponse checkReconciliationRecord(
        AdminPrincipal principal,
        String idempotencyKey,
        long reconciliationId,
        ReconciliationCheckCommand command
    ) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        ReconciliationRecordRow record = requireReconciliationRecord(reconciliationId);
        boolean targetChecked = command == null || command.checkedFlag() == null ? true : command.checkedFlag();
        if (record.checkedFlag() == targetChecked) {
            return mapReconciliationRecordResponse(record);
        }
        String differenceReason = command == null ? null : command.differenceReason();
        if (!"MATCHED".equals(record.result()) && targetChecked && (differenceReason == null || differenceReason.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "差异核对需填写差异原因");
        }
        jdbcTemplate.update(
            """
            update finance_reconciliation_record
            set checked_flag = ?,
                checked_by = ?,
                checked_at = ?,
                difference_reason = coalesce(?, difference_reason),
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            targetChecked ? 1 : 0,
            principal.userId(),
            LocalDateTime.now(),
            blankToNull(differenceReason),
            principal.userId(),
            reconciliationId);
        auditLogService.writeSuccess(
            principal,
            "RECONCILIATION",
            targetChecked ? "RECONCILIATION_CHECK" : "RECONCILIATION_UNCHECK",
            "FINANCE_RECONCILIATION_RECORD",
            record.id(),
            record.batchNo(),
            record.orderId(),
            "{\"checked_flag\":" + targetChecked + ",\"difference_reason\":\"" + sanitizeForJson(differenceReason) + "\"}");
        return mapReconciliationRecordResponse(requireReconciliationRecord(reconciliationId));
    }

    private ReconciliationRecordRow requireReconciliationRecord(long recordId) {
        return jdbcTemplate.query(
                """
                select id, batch_id, batch_no, bill_month, bill_source, record_type, order_id,
                       order_no, payment_id, refund_id, merchant_order_no, external_transaction_no,
                       system_amount_cent, bill_amount_cent, fee_amount_cent, result, difference_reason,
                       checked_flag
                from finance_reconciliation_record
                where id = ?
                """,
                (rs, rowNum) -> new ReconciliationRecordRow(
                    rs.getLong("id"),
                    rs.getLong("batch_id"),
                    rs.getString("batch_no"),
                    rs.getString("bill_month"),
                    rs.getString("bill_source"),
                    rs.getString("record_type"),
                    nullableLong(rs, "order_id"),
                    rs.getString("order_no"),
                    nullableLong(rs, "payment_id"),
                    nullableLong(rs, "refund_id"),
                    rs.getString("merchant_order_no"),
                    rs.getString("external_transaction_no"),
                    nullableLong(rs, "system_amount_cent"),
                    nullableLong(rs, "bill_amount_cent"),
                    nullableLong(rs, "fee_amount_cent"),
                    rs.getString("result"),
                    rs.getString("difference_reason"),
                    rs.getInt("checked_flag") == 1),
                recordId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "对账记录不存在"));
    }

    private ReconciliationRecordResponse mapReconciliationRecordResponse(ReconciliationRecordRow row) {
        return new ReconciliationRecordResponse(
            row.id(),
            row.batchId(),
            row.recordType(),
            row.orderId(),
            row.orderNo(),
            row.paymentId(),
            row.refundId(),
            row.merchantOrderNo(),
            row.externalTransactionNo(),
            row.systemAmountCent(),
            row.billAmountCent(),
            row.feeAmountCent(),
            row.result(),
            row.differenceReason());
    }

    private record ReconciliationRecordRow(
        long id, long batchId, String batchNo, String billMonth, String billSource, String recordType,
        Long orderId, String orderNo, Long paymentId, Long refundId, String merchantOrderNo, String externalTransactionNo,
        Long systemAmountCent, Long billAmountCent, Long feeAmountCent, String result, String differenceReason,
        boolean checkedFlag
    ) {
    }

    private static String sanitizeForJson(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    public AccountingMaterialPage accountingMaterials(AdminPrincipal principal, String status, String relatedMonth) {
        requireAdmin(principal);
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            select id, material_no, material_type, status, related_month, order_id, order_no,
                   related_object_type, related_object_id, related_object_no, request_user_id,
                   assignee_user_id, purpose, due_at, file_refs, uploaded_by, uploaded_at,
                   confirmed_by, confirmed_at, closed_reason
            from acct_material
            where 1 = 1
            """);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (relatedMonth != null && !relatedMonth.isBlank()) {
            sql.append(" and related_month = ?");
            args.add(relatedMonth.trim());
        }
        sql.append(" order by due_at asc, id desc");
        return new AccountingMaterialPage(jdbcTemplate.query(sql.toString(), this::mapMaterial, args.toArray()).stream()
            .map(this::toAccountingMaterialResponse)
            .toList());
    }

    public AccountingMaterialResponse accountingMaterialDetail(AdminPrincipal principal, long materialId) {
        requireAdmin(principal);
        return toAccountingMaterialResponse(requireMaterial(materialId));
    }

    @Transactional
    public CreationResult<AccountingMaterialResponse> createAccountingMaterial(AdminPrincipal principal, String idempotencyKey, AccountingMaterialCreateCommand command) {
        requireAdmin(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<AccountingMaterialRow> existing = findMaterialByIdempotency(key);
        if (existing.isPresent()) {
            return new CreationResult<>(toAccountingMaterialResponse(existing.get()), false);
        }
        Long orderId = command == null ? null : command.orderId();
        OrderRow order = orderId == null ? null : requireOrder(orderId);
        long materialId = idGenerator.nextId();
        String materialNo = "MAT" + materialId;
        String relatedObjectType = defaultString(command.relatedObjectType(), "ORDER");
        Long relatedObjectId = command.relatedObjectId() == null ? orderId : command.relatedObjectId();
        jdbcTemplate.update(
            """
            insert into acct_material (
                id, material_no, material_type, status, related_month, order_id, order_no,
                related_object_type, related_object_id, related_object_no, request_user_id,
                assignee_user_id, purpose, due_at, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, 'PENDING_SUPPLEMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            materialId,
            materialNo,
            normalizeText(command.materialType()),
            blankToNull(command.relatedMonth()),
            order == null ? null : order.id(),
            order == null ? null : order.orderNo(),
            normalizeText(relatedObjectType),
            relatedObjectId,
            relatedObjectId == null ? null : relatedObjectType + relatedObjectId,
            principal.userId(),
            principal.userId(),
            requireText(command.purpose(), "材料用途不能为空"),
            command.dueAt(),
            key,
            principal.userId(),
            principal.userId());
        insertMaterialRelation(materialId, materialNo, normalizeText(relatedObjectType), relatedObjectId, order, "PRIMARY", null);
        if (order != null) {
            upsertDocumentLink(order, "ACCOUNTING_MATERIAL", materialId, materialNo, "PENDING_SUPPLEMENT", null, "ACCOUNTING_MATERIAL_REQUEST", "acct_material", "代账材料申请", principal.userId());
        }
        auditLogService.writeSuccess(principal, "ACCOUNTING", "MATERIAL_CREATE", "ACCT_MATERIAL", materialId, materialNo, order == null ? null : order.id(), "{\"status\":\"PENDING_SUPPLEMENT\"}");
        return new CreationResult<>(toAccountingMaterialResponse(requireMaterial(materialId)), true);
    }

    @Transactional
    public AccountingMaterialResponse uploadAccountingMaterialFiles(AdminPrincipal principal, String idempotencyKey, long materialId, AccountingMaterialUploadCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        AccountingMaterialRow material = requireMaterial(materialId);
        String fileRefs = toJson(command == null || command.fileRefs() == null ? List.of() : command.fileRefs());
        jdbcTemplate.update(
            """
            update acct_material
            set status = 'UPLOADED',
                file_refs = ?,
                uploaded_by = ?,
                uploaded_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status in ('PENDING_SUPPLEMENT', 'UPLOADED')
            """,
            fileRefs,
            principal.userId(),
            LocalDateTime.now(),
            principal.userId(),
            materialId);
        auditLogService.writeSuccess(principal, "ACCOUNTING", "MATERIAL_UPLOAD", "ACCT_MATERIAL", material.id(), material.materialNo(), material.orderId(), "{\"status\":\"UPLOADED\"}");
        return toAccountingMaterialResponse(requireMaterial(materialId));
    }

    @Transactional
    public AccountingMaterialResponse confirmAccountingMaterial(AdminPrincipal principal, String idempotencyKey, long materialId, AccountingMaterialConfirmCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        AccountingMaterialRow material = requireMaterial(materialId);
        if ("CONFIRMED".equals(material.status())) {
            return toAccountingMaterialResponse(material);
        }
        jdbcTemplate.update(
            """
            update acct_material
            set status = 'CONFIRMED',
                confirmed_by = ?,
                confirmed_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status in ('PENDING_SUPPLEMENT', 'UPLOADED')
            """,
            principal.userId(),
            LocalDateTime.now(),
            principal.userId(),
            materialId);
        auditLogService.writeSuccess(principal, "ACCOUNTING", "MATERIAL_CONFIRM", "ACCT_MATERIAL", material.id(), material.materialNo(), material.orderId(), "{\"status\":\"CONFIRMED\"}");
        return toAccountingMaterialResponse(requireMaterial(materialId));
    }

    @Transactional
    public AccountingMaterialDownloadResponse downloadAccountingMaterial(AdminPrincipal principal, long materialId, String fileNo, String downloadReason) {
        requireAdmin(principal);
        AccountingMaterialRow material = requireMaterial(materialId);
        List<String> fileRefs = fileRefs(material.fileRefsJson());
        String normalizedFileNo = requireText(fileNo, "文件编号不能为空");
        if (!fileRefs.contains(normalizedFileNo)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "材料文件不存在");
        }
        auditLogService.writeSuccess(principal, "ACCOUNTING", "MATERIAL_DOWNLOAD", "ACCT_MATERIAL", material.id(), material.materialNo(), material.orderId(), "{\"file_no\":\"" + jsonSafe(normalizedFileNo) + "\",\"reason\":\"" + jsonSafe(defaultString(downloadReason, "未填写")) + "\"}");
        return new AccountingMaterialDownloadResponse(material.id(), material.materialNo(), normalizedFileNo, true, defaultString(downloadReason, "未填写"));
    }

    @Transactional
    public AccountingMaterialResponse closeAccountingMaterial(AdminPrincipal principal, String idempotencyKey, long materialId, AccountingMaterialCloseCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        AccountingMaterialRow material = requireMaterial(materialId);
        if ("CLOSED".equals(material.status())) {
            return toAccountingMaterialResponse(material);
        }
        jdbcTemplate.update(
            """
            update acct_material
            set status = 'CLOSED',
                closed_reason = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status <> 'CLOSED'
            """,
            requireText(command == null ? null : command.closedReason(), "关闭原因不能为空"),
            principal.userId(),
            materialId);
        auditLogService.writeSuccess(principal, "ACCOUNTING", "MATERIAL_CLOSE", "ACCT_MATERIAL", material.id(), material.materialNo(), material.orderId(), "{\"status\":\"CLOSED\"}");
        return toAccountingMaterialResponse(requireMaterial(materialId));
    }

    public AccountingWorkbenchSummaryResponse accountingWorkbenchSummary(AdminPrincipal principal, String relatedMonth) {
        requireAdmin(principal);
        MonthRange range = monthRange(relatedMonth);
        long incomeAmount = sumByTimeRange(
            "select coalesce(sum(paid_amount_cent), 0) from pay_payment where payment_result = 'SUCCESS'",
            "paid_at",
            range);
        long refundAmount = sumByTimeRange(
            "select coalesce(sum(coalesce(approved_amount_cent, apply_amount_cent)), 0) from pay_refund where status = 'REFUNDED'",
            "refunded_at",
            range);
        long purchaseAmount = sumByTimeRange(
            "select coalesce(sum(total_amount_cent), 0) from purchase_order where purchase_status = 'COMPLETED'",
            "created_at",
            range);
        long issuedInvoiceAmount = sumByTimeRange(
            "select coalesce(sum(invoice_amount_cent), 0) from tax_invoice where status in ('ISSUED', 'RED_REVERSED')",
            "issued_at",
            range);
        long redReversedInvoiceAmount = sumByTimeRange(
            "select coalesce(sum(invoice_amount_cent), 0) from tax_invoice where status = 'RED_REVERSED'",
            "red_reversed_at",
            range);
        int reconciliationDiffCount = countByTimeRange(
            "select count(*) from finance_reconciliation_record where result <> 'MATCHED'",
            "created_at",
            range);
        return new AccountingWorkbenchSummaryResponse(
            relatedMonth,
            incomeAmount,
            refundAmount,
            purchaseAmount,
            issuedInvoiceAmount,
            redReversedInvoiceAmount,
            reconciliationDiffCount,
            materialStatusCounts(relatedMonth));
    }

    @Transactional
    public CompensationRetryResponse compensationAction(AdminPrincipal principal, String idempotencyKey, long taskId, CompensationActionCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        String type = normalizeChoice(
            command == null ? null : command.compensationType(),
            List.of("REFUND", "INVOICE", "RED_REVERSE", "RECONCILIATION"),
            "补偿类型非法");
        String action = normalizeChoice(
            command == null ? null : command.action(),
            List.of("RETRY", "CLOSE"),
            "补偿动作非法");
        if ("RETRY".equals(action)) {
            String retryMode = normalizeText(command.retryMode());
            if ("REFUND".equals(type) && "MANUAL_REQUIRED".equals(retryMode)) {
                RefundRow refund = requireRefund(taskId);
                jdbcTemplate.update(
                    """
                    update pay_refund
                    set status = 'MANUAL_REQUIRED',
                        refund_channel = 'MANUAL',
                        updated_by = ?,
                        version = version + 1
                    where id = ? and status in ('FAILED', 'PROCESSING', 'MANUAL_REQUIRED')
                    """,
                    principal.userId(),
                    taskId);
                jdbcTemplate.update(
                    """
                    update trade_order
                    set refund_status = 'MANUAL_REQUIRED',
                        updated_by = ?,
                        version = version + 1
                    where id = ? and refund_status <> 'REFUNDED'
                    """,
                    principal.userId(),
                    refund.orderId());
                auditLogService.writeSuccess(principal, "COMPENSATION", "REFUND_TO_MANUAL", "PAY_REFUND", refund.id(), refund.refundNo(), refund.orderId(), "{\"status\":\"MANUAL_REQUIRED\"}");
                return new CompensationRetryResponse(taskId, "REFUND", "MANUAL_REQUIRED", "转人工退款");
            }
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "暂不支持该重试组合: " + type + "/" + retryMode);
        }
        // CLOSE
        String closedReason = command.closedReason();
        if (closedReason == null || closedReason.isBlank()) {
            closedReason = command.failureReason();
        }
        if (closedReason == null || closedReason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "关闭补偿必须填写原因");
        }
        if ("REFUND".equals(type)) {
            RefundRow refund = requireRefund(taskId);
            if (!List.of("FAILED", "MANUAL_REQUIRED").contains(refund.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "当前状态不允许关闭补偿: " + refund.status());
            }
            jdbcTemplate.update(
                """
                update pay_refund
                set failure_reason = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ?
                """,
                closedReason, principal.userId(), taskId);
            auditLogService.writeSuccess(principal, "COMPENSATION", "COMPENSATION_CLOSE", "PAY_REFUND", refund.id(), refund.refundNo(), refund.orderId(), "{\"closed_reason\":\"" + sanitizeForJson(closedReason) + "\"}");
            return new CompensationRetryResponse(taskId, "REFUND", "CLOSED", "补偿已关闭");
        }
        if ("INVOICE".equals(type) || "RED_REVERSE".equals(type)) {
            jdbcTemplate.update(
                """
                update tax_invoice
                set failure_reason = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ?
                """,
                closedReason, principal.userId(), taskId);
            auditLogService.writeSuccess(principal, "COMPENSATION", "COMPENSATION_CLOSE", "TAX_INVOICE", taskId, null, null, "{\"closed_reason\":\"" + sanitizeForJson(closedReason) + "\"}");
            return new CompensationRetryResponse(taskId, type, "CLOSED", "补偿已关闭");
        }
        if ("RECONCILIATION".equals(type)) {
            jdbcTemplate.update(
                """
                update finance_reconciliation_record
                set difference_reason = ?,
                    checked_flag = 1,
                    checked_by = ?,
                    checked_at = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ?
                """,
                closedReason, principal.userId(), LocalDateTime.now(), principal.userId(), taskId);
            auditLogService.writeSuccess(principal, "COMPENSATION", "COMPENSATION_CLOSE", "FINANCE_RECONCILIATION_RECORD", taskId, null, null, "{\"closed_reason\":\"" + sanitizeForJson(closedReason) + "\"}");
            return new CompensationRetryResponse(taskId, "RECONCILIATION", "CLOSED", "补偿已关闭");
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "未知补偿类型: " + type);
    }

    private void insertRefundItems(long refundId, String refundNo, OrderRow order, long amountCent, long operatorUserId) {
        List<OrderItemRow> items = orderItems(order.id());
        long assigned = 0;
        for (int i = 0; i < items.size(); i++) {
            OrderItemRow item = items.get(i);
            long itemAmount = i == items.size() - 1
                ? amountCent - assigned
                : Math.min(item.payableAmountCent(), Math.round((double) amountCent * item.payableAmountCent() / order.payableAmountCent()));
            assigned += itemAmount;
            jdbcTemplate.update(
                """
                insert into pay_refund_item (
                    id, refund_id, refund_no, order_id, order_item_id, course_id, spec_id,
                    refund_quantity, refund_amount_cent, item_snapshot, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId(),
                refundId,
                refundNo,
                order.id(),
                item.id(),
                item.courseId(),
                item.specId(),
                item.quantity(),
                itemAmount,
                defaultString(item.specSnapshotJson(), "{}"),
                operatorUserId,
                operatorUserId);
        }
    }

    private void runRefundSuccessSideEffects(RefundRow refund, LocalDateTime occurredAt, Long operatorUserId) {
        OrderRow order = requireOrder(refund.orderId());
        jdbcTemplate.update(
            """
            update trade_order
            set refund_status = 'REFUNDED',
                updated_by = ?,
                version = version + 1
            where id = ? and refund_status <> 'REFUNDED'
            """,
            nullToZero(operatorUserId),
            order.id());
        upsertDocumentLink(order, "REFUND", refund.id(), refund.refundNo(), "REFUNDED", refund.approvedAmountCent() == null ? refund.applyAmountCent() : refund.approvedAmountCent(), "REFUND_SUCCESS", "pay_refund", "退款成功", operatorUserId);
        for (OrderItemRow item : orderItems(order.id())) {
            try {
                learningEntitlementService.applyRefundEffect(new RefundEntitlementCommand(
                    order.studentId(),
                    order.id(),
                    item.courseId(),
                    refund.id(),
                    defaultString(refund.entitlementAction(), "FREEZE"),
                    occurredAt));
            } catch (RuntimeException exception) {
                auditLogService.writeSystemFailure("REFUND", "REFUND_ENTITLEMENT_EFFECT_FAILED", "PAY_REFUND", refund.id(), refund.refundNo(), order.id(), exception.getMessage());
            }
        }
        jdbcTemplate.update(
            """
            update notify_message
            set send_status = 'CANCELED',
                failure_reason = '退款成功停止提醒',
                updated_by = ?,
                version = version + 1
            where order_id = ? and send_status = 'PENDING'
            """,
            nullToZero(operatorUserId),
            order.id());
        jdbcTemplate.update(
            """
            update fulfillment_shipment
            set exception_flag = 1,
                exception_reason = case
                    when status = 'PENDING_SHIPMENT' then '退款成功，限制待发货'
                    else '退款成功，已发货售后待人工处理'
                end,
                updated_by = ?,
                version = version + 1
            where order_id = ? and exception_flag = 0
            """,
            nullToZero(operatorUserId),
            order.id());
        markIssuedInvoiceNeedRedReverse(refund, order, operatorUserId);
        AccountingMaterialRow material = createRefundAccountingMaterialIfNeeded(refund, order, operatorUserId);
        upsertDocumentLink(order, "ACCOUNTING_MATERIAL", material.id(), material.materialNo(), material.status(), null, "REFUND_SUCCESS", "acct_material", "退款进入代账材料", operatorUserId);
        auditLogService.writeSystemSuccess("REFUND", "REFUND_SUCCESS_SIDE_EFFECTS", "PAY_REFUND", refund.id(), refund.refundNo(), order.id(), "{\"entitlement_action\":\"" + defaultString(refund.entitlementAction(), "FREEZE") + "\"}");
    }

    private void markIssuedInvoiceNeedRedReverse(RefundRow refund, OrderRow order, Long operatorUserId) {
        List<InvoiceRow> invoices = invoicesByOrder(order.id());
        for (InvoiceRow invoice : invoices) {
            if ("ISSUED".equals(invoice.status()) && invoice.sourceRefundId() == null) {
                jdbcTemplate.update(
                    """
                    update tax_invoice
                    set source_refund_id = ?,
                        updated_by = ?,
                        version = version + 1
                    where id = ? and status = 'ISSUED' and source_refund_id is null
                    """,
                    refund.id(),
                    nullToZero(operatorUserId),
                    invoice.id());
                auditLogService.writeSystemSuccess("INVOICE", "RED_REVERSE_TODO_CREATE", "TAX_INVOICE", invoice.id(), invoice.invoiceApplyNo(), order.id(), "{\"source_refund_id\":" + refund.id() + "}");
            }
        }
    }

    private AccountingMaterialRow createRefundAccountingMaterialIfNeeded(RefundRow refund, OrderRow order, Long operatorUserId) {
        Optional<AccountingMaterialRow> existing = findMaterialByRelatedObject("REFUND", refund.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        long materialId = idGenerator.nextId();
        String materialNo = "MAT" + materialId;
        jdbcTemplate.update(
            """
            insert into acct_material (
                id, material_no, material_type, status, related_month, order_id, order_no,
                related_object_type, related_object_id, related_object_no, request_user_id,
                purpose, idempotency_key, created_by, updated_by
            ) values (?, ?, 'REFUND', 'PENDING_SUPPLEMENT', ?, ?, ?, 'REFUND', ?, ?, ?, ?, ?, ?, ?)
            """,
            materialId,
            materialNo,
            order.paidAt() == null ? null : order.paidAt().toLocalDate().toString().substring(0, 7),
            order.id(),
            order.orderNo(),
            refund.id(),
            refund.refundNo(),
            nullToZero(operatorUserId),
            "退款成功后财税入账材料",
            "REFUND:" + refund.id(),
            nullToZero(operatorUserId),
            nullToZero(operatorUserId));
        insertMaterialRelation(materialId, materialNo, "REFUND", refund.id(), order, "PRIMARY", refund.approvedAmountCent() == null ? refund.applyAmountCent() : refund.approvedAmountCent());
        auditLogService.writeSystemSuccess("ACCOUNTING", "REFUND_MATERIAL_CREATE", "ACCT_MATERIAL", materialId, materialNo, order.id(), "{\"refund_id\":" + refund.id() + "}");
        return requireMaterial(materialId);
    }

    private ReconciliationPreparedRecord prepareReconciliationRecord(
        String billMonth,
        String billSource,
        ReconciliationRecordCommand record,
        Set<String> seen
    ) {
        String recordType = normalizeChoice(record.recordType(), List.of("PAYMENT", "REFUND"), "对账记录类型非法");
        String merchantOrderNo = requireText(record.merchantOrderNo(), "商户订单号不能为空");
        String externalTransactionNo = requireText(record.externalTransactionNo(), "外部交易号不能为空");
        String uniqueKey = recordType + "|" + merchantOrderNo + "|" + externalTransactionNo;
        if (!seen.add(uniqueKey)) {
            return new ReconciliationPreparedRecord(recordType, null, null, null, null, merchantOrderNo, externalTransactionNo, null, record.billAmountCent(), record.feeAmountCent(), "DUPLICATE", "重复账单记录");
        }
        if ("PAYMENT".equals(recordType)) {
            Optional<PaymentWithOrderRow> payment = findPaymentByMerchantOrderNo(merchantOrderNo);
            if (payment.isEmpty()) {
                return new ReconciliationPreparedRecord(recordType, null, null, null, null, merchantOrderNo, externalTransactionNo, null, record.billAmountCent(), record.feeAmountCent(), "UNMATCHED", "系统支付单未匹配");
            }
            PaymentWithOrderRow row = payment.get();
            String result = "MATCHED";
            String reason = null;
            if (record.billAmountCent() == null || record.billAmountCent().longValue() != row.paidAmountCent()) {
                result = "AMOUNT_DIFF";
                reason = "支付金额差异";
            } else if (record.feeAmountCent() != null && record.feeAmountCent() != 0) {
                result = "FEE_DIFF";
                reason = "手续费差异";
            }
            return new ReconciliationPreparedRecord(recordType, row.orderId(), row.orderNo(), row.paymentId(), null, merchantOrderNo, externalTransactionNo, row.paidAmountCent(), record.billAmountCent(), record.feeAmountCent(), result, reason);
        }
        Optional<RefundRow> refund = findRefundByExternalNo(externalTransactionNo);
        if (refund.isEmpty()) {
            return new ReconciliationPreparedRecord(recordType, null, null, null, null, merchantOrderNo, externalTransactionNo, null, record.billAmountCent(), record.feeAmountCent(), "UNMATCHED", "系统退款单未匹配");
        }
        RefundRow row = refund.get();
        long systemAmount = row.approvedAmountCent() == null ? row.applyAmountCent() : row.approvedAmountCent();
        String result = record.billAmountCent() == null || record.billAmountCent().longValue() != systemAmount ? "AMOUNT_DIFF" : "MATCHED";
        return new ReconciliationPreparedRecord(recordType, row.orderId(), row.orderNo(), null, row.id(), merchantOrderNo, externalTransactionNo, systemAmount, record.billAmountCent(), record.feeAmountCent(), result, "MATCHED".equals(result) ? null : "退款金额差异");
    }

    private void insertReconciliationRecord(
        long batchId,
        String batchNo,
        String billMonth,
        String billSource,
        ReconciliationPreparedRecord record,
        long operatorUserId
    ) {
        long recordId = idGenerator.nextId();
        try {
            insertReconciliationRecordRow(recordId, batchId, batchNo, billMonth, billSource, record, operatorUserId);
        } catch (DuplicateKeyException duplicateKeyException) {
            ReconciliationPreparedRecord duplicate = new ReconciliationPreparedRecord(
                record.recordType(),
                record.orderId(),
                record.orderNo(),
                record.paymentId(),
                record.refundId(),
                record.merchantOrderNo(),
                record.externalTransactionNo() + "#DUP-" + recordId,
                record.systemAmountCent(),
                record.billAmountCent(),
                record.feeAmountCent(),
                "DUPLICATE",
                defaultString(record.differenceReason(), "重复账单记录"));
            insertReconciliationRecordRow(idGenerator.nextId(), batchId, batchNo, billMonth, billSource, duplicate, operatorUserId);
        }
    }

    private void insertReconciliationRecordRow(
        long recordId,
        long batchId,
        String batchNo,
        String billMonth,
        String billSource,
        ReconciliationPreparedRecord record,
        long operatorUserId
    ) {
        jdbcTemplate.update(
            """
            insert into finance_reconciliation_record (
                id, batch_id, batch_no, bill_month, bill_source, record_type,
                order_id, order_no, payment_id, refund_id, merchant_order_no,
                external_transaction_no, system_amount_cent, bill_amount_cent,
                fee_amount_cent, result, difference_reason, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            recordId,
            batchId,
            batchNo,
            billMonth,
            billSource,
            record.recordType(),
            record.orderId(),
            record.orderNo(),
            record.paymentId(),
            record.refundId(),
            record.merchantOrderNo(),
            record.externalTransactionNo(),
            record.systemAmountCent(),
            record.billAmountCent(),
            record.feeAmountCent(),
            record.result(),
            record.differenceReason(),
            operatorUserId,
            operatorUserId);
    }

    private void insertInvoiceItems(long invoiceId, String invoiceApplyNo, OrderRow order, long operatorUserId) {
        for (OrderItemRow item : orderItems(order.id())) {
            jdbcTemplate.update(
                """
                insert into tax_invoice_item (
                    id, invoice_id, invoice_apply_no, order_id, order_item_id,
                    item_name, item_type, amount_cent, tax_rate, tax_amount_cent,
                    tax_rule_id, tax_snapshot, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, 'COURSE', ?, ?, 0, null, ?, ?, ?)
                """,
                idGenerator.nextId(),
                invoiceId,
                invoiceApplyNo,
                order.id(),
                item.id(),
                item.itemName(),
                item.paidAmountCent() == null ? item.payableAmountCent() : item.paidAmountCent(),
                BigDecimal.ZERO,
                defaultString(item.taxSnapshotJson(), "{}"),
                operatorUserId,
                operatorUserId);
        }
    }

    private void insertMaterialRelation(
        long materialId,
        String materialNo,
        String relatedObjectType,
        Long relatedObjectId,
        OrderRow order,
        String relationRole,
        Long amountCent
    ) {
        if (relatedObjectId == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                """
                insert into acct_material_relation (
                    id, material_id, material_no, related_object_type, related_object_id,
                    related_object_no, order_id, order_no, relation_role, amount_cent
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idGenerator.nextId(),
                materialId,
                materialNo,
                relatedObjectType,
                relatedObjectId,
                relatedObjectType + relatedObjectId,
                order == null ? null : order.id(),
                order == null ? null : order.orderNo(),
                relationRole,
                amountCent);
        } catch (DuplicateKeyException ignored) {
            // relation exists, idempotent no-op
        }
    }

    private void upsertDocumentLink(
        OrderRow order,
        String documentType,
        long documentId,
        String documentNo,
        String documentStatus,
        Long amountCent,
        String relationType,
        String sourceTable,
        String remark,
        Long operatorUserId
    ) {
        documentLinkRepository.upsert(new DocumentLinkCommand(
            idGenerator.nextId(),
            order.id(),
            order.orderNo(),
            documentType,
            documentId,
            documentNo,
            documentStatus,
            amountCent,
            relationType,
            LocalDateTime.now(),
            sourceTable,
            remark,
            operatorUserId));
    }

    private OrderRow requireStudentOrder(long orderId, long studentId) {
        OrderRow order = requireOrder(orderId);
        if (order.studentId() != studentId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该订单");
        }
        return order;
    }

    private OrderRow requireOrder(long orderId) {
        return jdbcTemplate.query(
            """
            select id, order_no, merchant_order_no, student_id, user_id, total_amount_cent,
                   payable_amount_cent, paid_amount_cent, tax_snapshot, payment_status,
                   fulfillment_status, refund_status, invoice_status, paid_at
            from trade_order
            where id = ?
            """,
            this::mapOrder,
            orderId).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "订单不存在"));
    }

    private OrderRow mapOrder(ResultSet rs, int rowNum) throws SQLException {
        return new OrderRow(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getString("merchant_order_no"),
            rs.getLong("student_id"),
            rs.getLong("user_id"),
            rs.getLong("total_amount_cent"),
            rs.getLong("payable_amount_cent"),
            nullableLong(rs, "paid_amount_cent"),
            rs.getString("tax_snapshot"),
            rs.getString("payment_status"),
            rs.getString("fulfillment_status"),
            rs.getString("refund_status"),
            rs.getString("invoice_status"),
            rs.getObject("paid_at", LocalDateTime.class));
    }

    private PaymentRow requireSuccessPayment(long orderId) {
        return jdbcTemplate.query(
            """
            select id, payment_no, order_id, order_no, merchant_order_no, paid_amount_cent
            from pay_payment
            where order_id = ? and payment_result = 'SUCCESS'
            order by paid_at desc, id desc
            limit 1
            """,
            (rs, rowNum) -> new PaymentRow(
                rs.getLong("id"),
                rs.getString("payment_no"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getString("merchant_order_no"),
                rs.getLong("paid_amount_cent")),
            orderId).stream().findFirst().orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "支付记录不存在"));
    }

    private List<OrderItemRow> orderItems(long orderId) {
        return jdbcTemplate.query(
            """
            select id, order_id, order_no, line_no, course_id, spec_id, item_name,
                   quantity, payable_amount_cent, paid_amount_cent, spec_snapshot, tax_snapshot
            from trade_order_item
            where order_id = ?
            order by line_no
            """,
            (rs, rowNum) -> new OrderItemRow(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getInt("line_no"),
                rs.getLong("course_id"),
                rs.getLong("spec_id"),
                rs.getString("item_name"),
                rs.getInt("quantity"),
                rs.getLong("payable_amount_cent"),
                nullableLong(rs, "paid_amount_cent"),
                rs.getString("spec_snapshot"),
                rs.getString("tax_snapshot")),
            orderId);
    }

    private RefundRow requireRefund(long refundId) {
        return jdbcTemplate.query(refundSql("id = ?"), this::mapRefund, refundId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "退款单不存在"));
    }

    private Optional<RefundRow> findRefundByNo(String refundNo) {
        return jdbcTemplate.query(refundSql("refund_no = ?"), this::mapRefund, refundNo).stream().findFirst();
    }

    private Optional<RefundRow> findRefundByExternalNo(String externalRefundNo) {
        return jdbcTemplate.query(refundSql("external_refund_no = ?"), this::mapRefund, externalRefundNo).stream().findFirst();
    }

    private Optional<RefundRow> findRefundByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query(refundSql("idempotency_key = ?"), this::mapRefund, idempotencyKey).stream().findFirst();
    }

    private String refundSql(String condition) {
        return """
            select id, refund_no, order_id, order_no, payment_id, student_id, apply_amount_cent,
                   approved_amount_cent, refund_reason, apply_description, status, reviewer_user_id,
                   review_comment, reject_reason, refund_channel, external_refund_no,
                   manual_voucher_no, manual_voucher_file, failure_reason, refunded_at,
                   entitlement_action, created_at
            from pay_refund
            where
            """ + " " + condition;
    }

    private RefundRow mapRefund(ResultSet rs, int rowNum) throws SQLException {
        return new RefundRow(
            rs.getLong("id"),
            rs.getString("refund_no"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getLong("payment_id"),
            rs.getLong("student_id"),
            rs.getLong("apply_amount_cent"),
            nullableLong(rs, "approved_amount_cent"),
            rs.getString("refund_reason"),
            rs.getString("apply_description"),
            rs.getString("status"),
            nullableLong(rs, "reviewer_user_id"),
            rs.getString("review_comment"),
            rs.getString("reject_reason"),
            rs.getString("refund_channel"),
            rs.getString("external_refund_no"),
            rs.getString("manual_voucher_no"),
            rs.getString("manual_voucher_file"),
            rs.getString("failure_reason"),
            rs.getObject("refunded_at", LocalDateTime.class),
            rs.getString("entitlement_action"),
            rs.getObject("created_at", LocalDateTime.class));
    }

    private InvoiceTitleRow requireInvoiceTitle(long titleId) {
        return jdbcTemplate.query(invoiceTitleSql("id = ?"), this::mapInvoiceTitle, titleId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "发票抬头不存在"));
    }

    private InvoiceTitleRow requireStudentInvoiceTitle(Long titleId, long studentId) {
        if (titleId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "发票抬头不能为空");
        }
        InvoiceTitleRow title = requireInvoiceTitle(titleId);
        if (title.studentId() != studentId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权使用该发票抬头");
        }
        return title;
    }

    private Optional<InvoiceTitleRow> findInvoiceTitleByIdempotency(long studentId, String idempotencyKey) {
        return jdbcTemplate.query(invoiceTitleSql("student_id = ? and idempotency_key = ?"), this::mapInvoiceTitle, studentId, idempotencyKey).stream().findFirst();
    }

    private String invoiceTitleSql(String condition) {
        return """
            select id, student_id, title_type, title_name, tax_no, email, is_default, status
            from student_invoice_title
            where deleted_flag = 0 and
            """ + " " + condition;
    }

    private InvoiceTitleRow mapInvoiceTitle(ResultSet rs, int rowNum) throws SQLException {
        return new InvoiceTitleRow(
            rs.getLong("id"),
            rs.getLong("student_id"),
            rs.getString("title_type"),
            rs.getString("title_name"),
            rs.getString("tax_no"),
            rs.getString("email"),
            rs.getInt("is_default") == 1,
            rs.getString("status"));
    }

    private InvoiceRow requireInvoice(long invoiceId) {
        return jdbcTemplate.query(invoiceSql("id = ?"), this::mapInvoice, invoiceId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "发票单不存在"));
    }

    private Optional<InvoiceRow> findInvoiceByIdempotency(long studentId, String idempotencyKey) {
        return jdbcTemplate.query(invoiceSql("student_id = ? and idempotency_key = ?"), this::mapInvoice, studentId, idempotencyKey).stream().findFirst();
    }

    private Optional<InvoiceRow> findInvoiceByApplyNo(String invoiceApplyNo) {
        return jdbcTemplate.query(invoiceSql("invoice_apply_no = ?"), this::mapInvoice, invoiceApplyNo).stream().findFirst();
    }

    private List<InvoiceRow> invoicesByOrder(long orderId) {
        return jdbcTemplate.query(invoiceSql("order_id = ?"), this::mapInvoice, orderId);
    }

    private String invoiceSql(String condition) {
        return """
            select id, invoice_apply_no, order_id, order_no, student_id, title_type, title_name,
                   tax_no, email, invoice_amount_cent, status, invoice_channel, invoice_no,
                   invoice_file, issued_at, source_refund_id, red_invoice_no, red_invoice_file,
                   red_reversed_at, failure_reason, created_at
            from tax_invoice
            where
            """ + " " + condition;
    }

    private InvoiceRow mapInvoice(ResultSet rs, int rowNum) throws SQLException {
        return new InvoiceRow(
            rs.getLong("id"),
            rs.getString("invoice_apply_no"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getLong("student_id"),
            rs.getString("title_type"),
            rs.getString("title_name"),
            rs.getString("tax_no"),
            rs.getString("email"),
            rs.getLong("invoice_amount_cent"),
            rs.getString("status"),
            rs.getString("invoice_channel"),
            rs.getString("invoice_no"),
            rs.getString("invoice_file"),
            rs.getObject("issued_at", LocalDateTime.class),
            nullableLong(rs, "source_refund_id"),
            rs.getString("red_invoice_no"),
            rs.getString("red_invoice_file"),
            rs.getObject("red_reversed_at", LocalDateTime.class),
            rs.getString("failure_reason"),
            rs.getObject("created_at", LocalDateTime.class));
    }

    private Optional<PaymentWithOrderRow> findPaymentByMerchantOrderNo(String merchantOrderNo) {
        return jdbcTemplate.query(
            """
            select p.id as payment_id, p.order_id, p.order_no, p.paid_amount_cent
            from pay_payment p
            where p.merchant_order_no = ? and p.payment_result = 'SUCCESS'
            order by p.paid_at desc, p.id desc
            limit 1
            """,
            (rs, rowNum) -> new PaymentWithOrderRow(
                rs.getLong("payment_id"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getLong("paid_amount_cent")),
            merchantOrderNo).stream().findFirst();
    }

    private Optional<ReconciliationBatchRow> findReconciliationByIdempotency(String idempotencyKey) {
        return jdbcTemplate.query(reconciliationBatchSql("idempotency_key = ?"), this::mapReconciliationBatch, idempotencyKey).stream().findFirst();
    }

    private ReconciliationBatchRow requireReconciliationBatch(long batchId) {
        return jdbcTemplate.query(reconciliationBatchSql("id = ?"), this::mapReconciliationBatch, batchId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "对账批次不存在"));
    }

    private String reconciliationBatchSql(String condition) {
        return """
            select id, batch_no, bill_month, bill_source, file_name, file_digest, import_status,
                   total_count, matched_count, diff_count, imported_by, imported_at, failure_reason
            from finance_reconciliation_batch
            where
            """ + " " + condition;
    }

    private ReconciliationBatchRow mapReconciliationBatch(ResultSet rs, int rowNum) throws SQLException {
        return new ReconciliationBatchRow(
            rs.getLong("id"),
            rs.getString("batch_no"),
            rs.getString("bill_month"),
            rs.getString("bill_source"),
            rs.getString("file_name"),
            rs.getString("file_digest"),
            rs.getString("import_status"),
            rs.getInt("total_count"),
            rs.getInt("matched_count"),
            rs.getInt("diff_count"),
            nullableLong(rs, "imported_by"),
            rs.getObject("imported_at", LocalDateTime.class),
            rs.getString("failure_reason"));
    }

    private List<ReconciliationRecordResponse> reconciliationRecords(long batchId) {
        return jdbcTemplate.query(
            """
            select id, batch_id, batch_no, bill_month, bill_source, record_type, order_id,
                   order_no, payment_id, refund_id, merchant_order_no, external_transaction_no,
                   system_amount_cent, bill_amount_cent, fee_amount_cent, result, difference_reason
            from finance_reconciliation_record
            where batch_id = ?
            order by id
            """,
            (rs, rowNum) -> new ReconciliationRecordResponse(
                rs.getLong("id"),
                rs.getLong("batch_id"),
                rs.getString("record_type"),
                nullableLong(rs, "order_id"),
                rs.getString("order_no"),
                nullableLong(rs, "payment_id"),
                nullableLong(rs, "refund_id"),
                rs.getString("merchant_order_no"),
                rs.getString("external_transaction_no"),
                nullableLong(rs, "system_amount_cent"),
                nullableLong(rs, "bill_amount_cent"),
                nullableLong(rs, "fee_amount_cent"),
                rs.getString("result"),
                rs.getString("difference_reason")),
            batchId);
    }

    private Optional<AccountingMaterialRow> findMaterialByIdempotency(String idempotencyKey) {
        return jdbcTemplate.query(materialSql("idempotency_key = ?"), this::mapMaterial, idempotencyKey).stream().findFirst();
    }

    private Optional<AccountingMaterialRow> findMaterialByRelatedObject(String relatedObjectType, long relatedObjectId) {
        return jdbcTemplate.query(materialSql("related_object_type = ? and related_object_id = ?"), this::mapMaterial, relatedObjectType, relatedObjectId).stream().findFirst();
    }

    private AccountingMaterialRow requireMaterial(long materialId) {
        return jdbcTemplate.query(materialSql("id = ?"), this::mapMaterial, materialId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "代账材料不存在"));
    }

    private String materialSql(String condition) {
        return """
            select id, material_no, material_type, status, related_month, order_id, order_no,
                   related_object_type, related_object_id, related_object_no, request_user_id,
                   assignee_user_id, purpose, due_at, file_refs, uploaded_by, uploaded_at,
                   confirmed_by, confirmed_at, closed_reason
            from acct_material
            where
            """ + " " + condition;
    }

    private AccountingMaterialRow mapMaterial(ResultSet rs, int rowNum) throws SQLException {
        return new AccountingMaterialRow(
            rs.getLong("id"),
            rs.getString("material_no"),
            rs.getString("material_type"),
            rs.getString("status"),
            rs.getString("related_month"),
            nullableLong(rs, "order_id"),
            rs.getString("order_no"),
            rs.getString("related_object_type"),
            nullableLong(rs, "related_object_id"),
            rs.getString("related_object_no"),
            rs.getLong("request_user_id"),
            nullableLong(rs, "assignee_user_id"),
            rs.getString("purpose"),
            rs.getObject("due_at", LocalDateTime.class),
            rs.getString("file_refs"),
            nullableLong(rs, "uploaded_by"),
            rs.getObject("uploaded_at", LocalDateTime.class),
            nullableLong(rs, "confirmed_by"),
            rs.getObject("confirmed_at", LocalDateTime.class),
            rs.getString("closed_reason"));
    }

    private RefundResponse toRefundResponse(RefundRow row) {
        return new RefundResponse(
            row.id(),
            row.refundNo(),
            row.orderId(),
            row.orderNo(),
            row.applyAmountCent(),
            row.approvedAmountCent(),
            row.status(),
            row.refundChannel(),
            row.externalRefundNo(),
            row.manualVoucherNo(),
            row.manualVoucherFile(),
            row.failureReason(),
            row.entitlementAction(),
            row.refundedAt());
    }

    private RefundCallbackResponse refundCallbackResponse(CallbackEventRecord event, RefundRow refund) {
        return new RefundCallbackResponse(
            event.processingStatus(),
            refund == null ? null : refund.id(),
            refund == null ? null : refund.refundNo(),
            refund == null ? null : refund.status(),
            event.failureReason());
    }

    private InvoiceTitleResponse toInvoiceTitleResponse(InvoiceTitleRow row) {
        return new InvoiceTitleResponse(row.id(), row.titleType(), row.titleName(), row.taxNo(), row.email(), row.isDefault(), row.status());
    }

    private InvoiceResponse toInvoiceResponse(InvoiceRow row) {
        return new InvoiceResponse(
            row.id(),
            row.invoiceApplyNo(),
            row.orderId(),
            row.orderNo(),
            row.invoiceAmountCent(),
            row.status(),
            row.invoiceChannel(),
            row.invoiceNo(),
            row.invoiceFile(),
            row.issuedAt(),
            row.sourceRefundId(),
            row.redInvoiceNo(),
            row.redInvoiceFile(),
            row.redReversedAt(),
            row.failureReason());
    }

    private InvoiceCallbackResponse invoiceCallbackResponse(CallbackEventRecord event, InvoiceRow invoice) {
        return new InvoiceCallbackResponse(
            event.processingStatus(),
            invoice == null ? null : invoice.id(),
            invoice == null ? null : invoice.invoiceApplyNo(),
            invoice == null ? null : invoice.status(),
            event.failureReason());
    }

    private ReconciliationBatchResponse toReconciliationBatchResponse(ReconciliationBatchRow row, List<ReconciliationRecordResponse> records) {
        return new ReconciliationBatchResponse(
            row.id(),
            row.batchNo(),
            row.billMonth(),
            row.billSource(),
            row.fileName(),
            row.importStatus(),
            row.totalCount(),
            row.matchedCount(),
            row.diffCount(),
            records);
    }

    private AccountingMaterialResponse toAccountingMaterialResponse(AccountingMaterialRow row) {
        return new AccountingMaterialResponse(
            row.id(),
            row.materialNo(),
            row.materialType(),
            row.status(),
            row.relatedMonth(),
            row.orderId(),
            row.orderNo(),
            row.relatedObjectType(),
            row.relatedObjectId(),
            row.purpose(),
            fileRefs(row.fileRefsJson()),
            row.dueAt(),
            row.uploadedAt(),
            row.confirmedAt());
    }

    private List<String> fileRefs(String fileRefsJson) {
        if (fileRefsJson == null || fileRefsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(fileRefsJson);
            if (node.isTextual()) {
                return fileRefs(node.asText());
            }
            if (!node.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            node.forEach(item -> result.add(item.asText()));
            return result;
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private Map<String, Integer> materialStatusCounts(String relatedMonth) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            """
            select status, count(*) as count_value
            from acct_material
            where 1 = 1
            """);
        if (relatedMonth != null && !relatedMonth.isBlank()) {
            sql.append(" and related_month = ?");
            args.add(relatedMonth.trim());
        }
        sql.append(" group by status");
        Map<String, Integer> counts = new LinkedHashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            counts.put(rs.getString("status"), rs.getInt("count_value"));
        }, args.toArray());
        return counts;
    }

    private long sumByTimeRange(String baseSql, String timeColumn, MonthRange range) {
        return queryLongByTimeRange(baseSql, timeColumn, range);
    }

    private int countByTimeRange(String baseSql, String timeColumn, MonthRange range) {
        return Math.toIntExact(queryLongByTimeRange(baseSql, timeColumn, range));
    }

    private long queryLongByTimeRange(String baseSql, String timeColumn, MonthRange range) {
        if (range == null) {
            Long value = jdbcTemplate.queryForObject(baseSql, Long.class);
            return value == null ? 0L : value;
        }
        Long value = jdbcTemplate.queryForObject(
            baseSql + " and " + timeColumn + " >= ? and " + timeColumn + " < ?",
            Long.class,
            range.startAt(),
            range.endAt());
        return value == null ? 0L : value;
    }

    private MonthRange monthRange(String relatedMonth) {
        if (relatedMonth == null || relatedMonth.isBlank()) {
            return null;
        }
        try {
            YearMonth yearMonth = YearMonth.parse(relatedMonth.trim());
            return new MonthRange(yearMonth.atDay(1).atStartOfDay(), yearMonth.plusMonths(1).atDay(1).atStartOfDay());
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "月份格式应为 yyyy-MM");
        }
    }

    private void validateRefundCallback(RefundCallbackCommand command) {
        if (command == null
            || command.eventNo() == null || command.eventNo().isBlank()
            || command.refundNo() == null || command.refundNo().isBlank()
            || command.refundStatus() == null || command.refundStatus().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "退款回调关键字段不能为空");
        }
    }

    private void validateInvoiceIssueCallback(InvoiceIssueCallbackCommand command) {
        if (command == null
            || command.eventNo() == null || command.eventNo().isBlank()
            || command.invoiceApplyNo() == null || command.invoiceApplyNo().isBlank()
            || command.status() == null || command.status().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "开票回调关键字段不能为空");
        }
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "缺少 Idempotency-Key");
        }
        if (value.trim().length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "幂等键过长");
        }
        return value.trim();
    }

    private long positive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value.trim();
    }

    private String normalizeChoice(String value, List<String> allowed, String message) {
        String normalized = normalizeText(value);
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return requireText(value, "参数不能为空").toUpperCase();
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "JSON 生成失败");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return fromJson(node.asText());
            }
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }



































    private record OrderRow(long id, String orderNo, String merchantOrderNo, long studentId, long userId, long totalAmountCent, long payableAmountCent, Long paidAmountCent, String taxSnapshotJson, String paymentStatus, String fulfillmentStatus, String refundStatus, String invoiceStatus, LocalDateTime paidAt) {
    }

    private record PaymentRow(long paymentId, String paymentNo, long orderId, String orderNo, String merchantOrderNo, long paidAmountCent) {
    }

    private record PaymentWithOrderRow(long paymentId, long orderId, String orderNo, long paidAmountCent) {
    }

    private record OrderItemRow(long id, long orderId, String orderNo, int lineNo, long courseId, long specId, String itemName, int quantity, long payableAmountCent, Long paidAmountCent, String specSnapshotJson, String taxSnapshotJson) {
    }

    private record RefundRow(long id, String refundNo, long orderId, String orderNo, long paymentId, long studentId, long applyAmountCent, Long approvedAmountCent, String refundReason, String applyDescription, String status, Long reviewerUserId, String reviewComment, String rejectReason, String refundChannel, String externalRefundNo, String manualVoucherNo, String manualVoucherFile, String failureReason, LocalDateTime refundedAt, String entitlementAction, LocalDateTime createdAt) {
    }

    private record InvoiceTitleRow(long id, long studentId, String titleType, String titleName, String taxNo, String email, boolean isDefault, String status) {
    }

    private record InvoiceRow(long id, String invoiceApplyNo, long orderId, String orderNo, long studentId, String titleType, String titleName, String taxNo, String email, long invoiceAmountCent, String status, String invoiceChannel, String invoiceNo, String invoiceFile, LocalDateTime issuedAt, Long sourceRefundId, String redInvoiceNo, String redInvoiceFile, LocalDateTime redReversedAt, String failureReason, LocalDateTime createdAt) {
    }

    private record ReconciliationPreparedRecord(String recordType, Long orderId, String orderNo, Long paymentId, Long refundId, String merchantOrderNo, String externalTransactionNo, Long systemAmountCent, Long billAmountCent, Long feeAmountCent, String result, String differenceReason) {
    }

    private record ReconciliationBatchRow(long id, String batchNo, String billMonth, String billSource, String fileName, String fileDigest, String importStatus, int totalCount, int matchedCount, int diffCount, Long importedBy, LocalDateTime importedAt, String failureReason) {
    }

    private record AccountingMaterialRow(long id, String materialNo, String materialType, String status, String relatedMonth, Long orderId, String orderNo, String relatedObjectType, Long relatedObjectId, String relatedObjectNo, long requestUserId, Long assigneeUserId, String purpose, LocalDateTime dueAt, String fileRefsJson, Long uploadedBy, LocalDateTime uploadedAt, Long confirmedBy, LocalDateTime confirmedAt, String closedReason) {
    }

    private record MonthRange(LocalDateTime startAt, LocalDateTime endAt) {
    }
}
