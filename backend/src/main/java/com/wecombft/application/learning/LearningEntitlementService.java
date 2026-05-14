package com.wecombft.application.learning;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

import com.wecombft.application.command.learning.PaymentSuccessEntitlementCommand;
import com.wecombft.application.command.learning.RefundEntitlementCommand;
@Service
public class LearningEntitlementService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public LearningEntitlementService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public EntitlementEventResult openForPaidOrder(PaymentSuccessEntitlementCommand command) {
        Optional<EntitlementRow> existing = findByBusinessKey(command.studentId(), command.orderId(), command.courseId());
        if (existing.isPresent()) {
            EntitlementRow row = existing.get();
            return new EntitlementEventResult(row.id(), row.entitlementNo(), row.status(), true);
        }

        long entitlementId = idGenerator.nextId();
        String entitlementNo = "ENT" + entitlementId;
        jdbcTemplate.update(
            """
            insert into learning_entitlement (
                id, entitlement_no, student_id, user_id, order_id, order_no, order_item_id,
                course_id, spec_id, status, opened_at, remind_stopped, course_snapshot,
                created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 0, ?, 0, 0)
            """,
            entitlementId,
            entitlementNo,
            command.studentId(),
            command.userId(),
            command.orderId(),
            command.orderNo(),
            command.orderItemId(),
            command.courseId(),
            command.specId(),
            command.openedAt() == null ? LocalDateTime.now() : command.openedAt(),
            command.courseSnapshotJson());

        auditLogService.writeSystemSuccess(
            "LEARNING",
            "ENTITLEMENT_OPEN",
            "LEARNING_ENTITLEMENT",
            entitlementId,
            entitlementNo,
            command.orderId(),
            "{\"status\":\"ACTIVE\",\"order_no\":\"" + command.orderNo() + "\"}");
        return new EntitlementEventResult(entitlementId, entitlementNo, "ACTIVE", false);
    }

    @Transactional
    public EntitlementEventResult applyRefundEffect(RefundEntitlementCommand command) {
        EntitlementRow row = findByBusinessKey(command.studentId(), command.orderId(), command.courseId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "学习权益不存在"));

        String targetStatus = switch (command.action()) {
            case "FREEZE" -> "FROZEN";
            case "REVOKE" -> "REVOKED";
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "权益动作不支持");
        };
        if (row.status().equals(targetStatus) || ("FROZEN".equals(targetStatus) && "REVOKED".equals(row.status()))) {
            return new EntitlementEventResult(row.id(), row.entitlementNo(), row.status(), true);
        }

        if ("FREEZE".equals(command.action())) {
            jdbcTemplate.update(
                """
                update learning_entitlement
                set status = 'FROZEN',
                    frozen_at = ?,
                    source_refund_id = ?,
                    remind_stopped = 1,
                    updated_by = 0,
                    version = version + 1
                where id = ?
                """,
                command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt(),
                command.refundId(),
                row.id());
        } else {
            jdbcTemplate.update(
                """
                update learning_entitlement
                set status = 'REVOKED',
                    revoked_at = ?,
                    source_refund_id = ?,
                    remind_stopped = 1,
                    updated_by = 0,
                    version = version + 1
                where id = ?
                """,
                command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt(),
                command.refundId(),
                row.id());
        }

        auditLogService.writeSystemSuccess(
            "LEARNING",
            "ENTITLEMENT_" + targetStatus,
            "LEARNING_ENTITLEMENT",
            row.id(),
            row.entitlementNo(),
            command.orderId(),
            "{\"status\":\"" + targetStatus + "\",\"refund_id\":" + command.refundId() + "}");
        return new EntitlementEventResult(row.id(), row.entitlementNo(), targetStatus, false);
    }

    private Optional<EntitlementRow> findByBusinessKey(long studentId, long orderId, long courseId) {
        return jdbcTemplate.query(
                """
                select id, entitlement_no, status
                from learning_entitlement
                where student_id = ? and order_id = ? and course_id = ?
                """,
                (rs, rowNum) -> new EntitlementRow(
                    rs.getLong("id"),
                    rs.getString("entitlement_no"),
                    rs.getString("status")),
                studentId,
                orderId,
                courseId)
            .stream()
            .findFirst();
    }

    private record EntitlementRow(long id, String entitlementNo, String status) {
    }



}
