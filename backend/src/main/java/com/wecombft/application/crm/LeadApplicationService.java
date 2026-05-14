package com.wecombft.application.crm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class LeadApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public LeadApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public LeadResponse createPublicLead(PublicLeadCommand command) {
        validateMobile(command.mobile());
        long leadId = idGenerator.nextId();
        String leadNo = "LEAD" + leadId;
        jdbcTemplate.update(
            """
            insert into crm_lead (
                id, lead_no, name, mobile, source_channel, source_code, intent_course_id,
                owner_user_id, status, match_exception_flag, created_by, updated_by
            ) values (?, ?, ?, ?, 'PUBLIC_H5', ?, ?, null, 'PENDING_FOLLOW', 0, 0, 0)
            """,
            leadId,
            leadNo,
            command.name(),
            command.mobile(),
            command.sourceCode(),
            command.intentCourseId());
        auditLogService.writeSystemSuccess(
            "CRM",
            "LEAD_CREATE",
            "CRM_LEAD",
            leadId,
            leadNo,
            null,
            "{\"source_channel\":\"PUBLIC_H5\"}");
        return findLead(leadId).map(this::toLeadResponse).orElseThrow();
    }

    @Transactional
    public LeadResponse createAdminLead(LeadSaveCommand command) {
        AdminPrincipal principal = currentPrincipal();
        validateMobile(command.mobile());
        if (isOpsOnly(principal) && command.ownerUserId() != null && command.ownerUserId() != principal.userId()) {
            auditLogService.writeFailure(
                principal,
                "CRM",
                "DATA_SCOPE_DENIED",
                "CRM_LEAD",
                null,
                null,
                null,
                "运营只能创建本人负责线索");
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "运营只能创建本人负责线索");
        }
        long leadId = idGenerator.nextId();
        String leadNo = "LEAD" + leadId;
        Long ownerUserId = command.ownerUserId() == null ? principal.userId() : command.ownerUserId();
        jdbcTemplate.update(
            """
            insert into crm_lead (
                id, lead_no, name, mobile, source_channel, source_code, intent_course_id,
                owner_user_id, wecom_external_user_id, status, match_exception_flag,
                next_follow_at, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_FOLLOW', 0, ?, ?, ?)
            """,
            leadId,
            leadNo,
            command.name(),
            command.mobile(),
            command.sourceChannel() == null ? "PC_ADMIN" : command.sourceChannel(),
            command.sourceCode(),
            command.intentCourseId(),
            ownerUserId,
            command.wecomExternalUserId(),
            command.nextFollowAt(),
            principal.userId(),
            principal.userId());
        auditLogService.writeSuccess(
            principal,
            "CRM",
            "LEAD_CREATE",
            "CRM_LEAD",
            leadId,
            leadNo,
            null,
            "{\"source_channel\":\"" + (command.sourceChannel() == null ? "PC_ADMIN" : command.sourceChannel()) + "\"}");
        return findLead(leadId).map(this::toLeadResponse).orElseThrow();
    }

    @Transactional
    public FollowRecordResponse createFollowRecord(long leadId, String idempotencyKey, FollowRecordCommand command) {
        AdminPrincipal principal = currentPrincipal();
        LeadRow lead = requireLead(leadId);
        ensureLeadWritable(principal, lead);
        if ("ABANDONED".equals(lead.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "已放弃线索需先重新跟进");
        }
        List<FollowRecordResponse> existing = jdbcTemplate.query(
            """
            select id, lead_id, follow_method, content, next_follow_at
            from lead_follow_record
            where idempotency_key = ?
            """,
            (rs, rowNum) -> new FollowRecordResponse(
                rs.getLong("id"),
                rs.getLong("lead_id"),
                rs.getString("follow_method"),
                rs.getString("content"),
                rs.getObject("next_follow_at", LocalDateTime.class),
                lead.status()),
            idempotencyKey);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        long followId = idGenerator.nextId();
        jdbcTemplate.update(
            """
            insert into lead_follow_record (
                id, lead_id, follow_method, content, next_follow_at,
                follower_user_id, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            followId,
            leadId,
            command.followMethod(),
            command.content(),
            command.nextFollowAt(),
            principal.userId(),
            idempotencyKey,
            principal.userId(),
            principal.userId());
        jdbcTemplate.update(
            """
            update crm_lead
            set status = case when status = 'PENDING_FOLLOW' then 'CONTACTED' else status end,
                latest_follow_at = ?,
                next_follow_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            LocalDateTime.now(),
            command.nextFollowAt(),
            principal.userId(),
            leadId);
        auditLogService.writeSuccess(
            principal,
            "CRM",
            "LEAD_FOLLOW",
            "CRM_LEAD",
            leadId,
            lead.leadNo(),
            null,
            "{\"follow_id\":" + followId + "}");
        LeadRow updated = requireLead(leadId);
        return new FollowRecordResponse(
            followId,
            leadId,
            command.followMethod(),
            command.content(),
            command.nextFollowAt(),
            updated.status());
    }

    @Transactional
    public LeadResponse updateLeadStatus(long leadId, LeadStatusCommand command) {
        AdminPrincipal principal = currentPrincipal();
        LeadRow lead = requireLead(leadId);
        ensureLeadWritable(principal, lead);
        String target = command.targetStatus();
        if ("ABANDONED".equals(target)) {
            if ("CONVERTED".equals(lead.status())) {
                throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "已转化线索不可放弃");
            }
            jdbcTemplate.update(
                """
                update crm_lead
                set status = 'ABANDONED',
                    abandon_reason = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ?
                """,
                command.abandonReason(),
                principal.userId(),
                leadId);
            auditLogService.writeSuccess(principal, "CRM", "LEAD_ABANDON", "CRM_LEAD", leadId, lead.leadNo(), null, "{}");
            return findLead(leadId).map(this::toLeadResponse).orElseThrow();
        }
        if ("CONTACTED".equals(target)) {
            if (command.nextFollowAt() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "重新跟进必须填写下次跟进时间");
            }
            jdbcTemplate.update(
                """
                update crm_lead
                set status = 'CONTACTED',
                    abandon_reason = null,
                    next_follow_at = ?,
                    latest_follow_at = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ?
                """,
                command.nextFollowAt(),
                LocalDateTime.now(),
                principal.userId(),
                leadId);
            auditLogService.writeSuccess(principal, "CRM", "LEAD_RECONTACT", "CRM_LEAD", leadId, lead.leadNo(), null, "{}");
            return findLead(leadId).map(this::toLeadResponse).orElseThrow();
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "线索目标状态不支持");
    }

    public LeadPage searchAdminLeads(String keyword, String status, Integer pageNo, Integer pageSize) {
        AdminPrincipal principal = currentPrincipal();
        StringBuilder sql = new StringBuilder(
            """
            select id, lead_no, name, mobile, source_channel, source_code, intent_course_id,
                   owner_user_id, wecom_external_user_id, status, match_exception_flag,
                   next_follow_at, latest_follow_at, student_id, converted_order_id, abandon_reason
            from crm_lead
            where deleted_flag = 0
            """);
        List<Object> args = new ArrayList<>();
        if (isOpsOnly(principal)) {
            sql.append(" and owner_user_id = ?");
            args.add(principal.userId());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and (name like ? or mobile like ? or source_code like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by id desc limit ? offset ?");
        int size = pageSize == null ? 20 : pageSize;
        int page = pageNo == null ? 1 : pageNo;
        args.add(size);
        args.add((page - 1) * size);
        List<LeadResponse> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toLeadResponse(mapLead(rs)), args.toArray());
        return new LeadPage(records, page, size, records.size());
    }

    @Transactional
    public LeadPaidConversionResponse convertPaidLead(LeadPaidConversionCommand command) {
        if (command.studentId() == null || command.orderId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "学员和订单不能为空");
        }
        if ((command.wecomExternalUserId() == null || command.wecomExternalUserId().isBlank())
            && (command.mobile() == null || command.mobile().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "缺少线索匹配依据");
        }
        if (command.mobile() != null && !command.mobile().isBlank()) {
            validateMobile(command.mobile());
        }

        java.util.Optional<LeadRow> converted = findConvertedLead(command.studentId(), command.orderId());
        if (converted.isPresent()) {
            LeadRow lead = converted.get();
            return new LeadPaidConversionResponse(
                true,
                lead.id(),
                lead.leadNo(),
                lead.status(),
                command.studentId(),
                command.orderId(),
                true,
                null);
        }

        java.util.Optional<LeadRow> candidate = findConversionCandidate(command.wecomExternalUserId(), command.mobile());
        if (candidate.isEmpty()) {
            auditLogService.writeSystemSuccess(
                "CRM",
                "LEAD_MATCH_MISS",
                "CRM_LEAD",
                null,
                null,
                command.orderId(),
                "{\"mobile\":\"" + (command.mobile() == null ? "" : command.mobile()) + "\"}");
            return new LeadPaidConversionResponse(false, null, null, null, command.studentId(), command.orderId(), false, "NO_MATCH");
        }

        LeadRow lead = candidate.get();
        jdbcTemplate.update(
            """
            update crm_lead
            set status = 'CONVERTED',
                student_id = ?,
                converted_order_id = ?,
                converted_at = ?,
                updated_by = 0,
                version = version + 1
            where id = ? and status <> 'CONVERTED'
            """,
            command.studentId(),
            command.orderId(),
            command.paidAt() == null ? LocalDateTime.now() : command.paidAt(),
            lead.id());
        jdbcTemplate.update(
            """
            update edu_student
            set primary_lead_id = coalesce(primary_lead_id, ?),
                updated_by = 0,
                version = version + 1
            where id = ?
            """,
            lead.id(),
            command.studentId());
        auditLogService.writeSystemSuccess(
            "CRM",
            "LEAD_CONVERTED",
            "CRM_LEAD",
            lead.id(),
            lead.leadNo(),
            command.orderId(),
            "{\"student_id\":" + command.studentId() + "}");
        return new LeadPaidConversionResponse(
            true,
            lead.id(),
            lead.leadNo(),
            "CONVERTED",
            command.studentId(),
            command.orderId(),
            false,
            null);
    }

    private LeadRow requireLead(long leadId) {
        return findLead(leadId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "线索不存在"));
    }

    private java.util.Optional<LeadRow> findLead(long leadId) {
        return jdbcTemplate.query(
                """
                select id, lead_no, name, mobile, source_channel, source_code, intent_course_id,
                       owner_user_id, wecom_external_user_id, status, match_exception_flag,
                       next_follow_at, latest_follow_at, student_id, converted_order_id, abandon_reason
                from crm_lead
                where id = ? and deleted_flag = 0
                """,
                (rs, rowNum) -> mapLead(rs),
                leadId)
            .stream()
            .findFirst();
    }

    private java.util.Optional<LeadRow> findConvertedLead(long studentId, long orderId) {
        return jdbcTemplate.query(
                """
                select id, lead_no, name, mobile, source_channel, source_code, intent_course_id,
                       owner_user_id, wecom_external_user_id, status, match_exception_flag,
                       next_follow_at, latest_follow_at, student_id, converted_order_id, abandon_reason
                from crm_lead
                where student_id = ? and converted_order_id = ? and status = 'CONVERTED' and deleted_flag = 0
                order by id desc
                limit 1
                """,
                (rs, rowNum) -> mapLead(rs),
                studentId,
                orderId)
            .stream()
            .findFirst();
    }

    private java.util.Optional<LeadRow> findConversionCandidate(String wecomExternalUserId, String mobile) {
        if (wecomExternalUserId != null && !wecomExternalUserId.isBlank()) {
            java.util.Optional<LeadRow> byExternalUser = queryConversionCandidate(
                "wecom_external_user_id = ?",
                wecomExternalUserId.trim());
            if (byExternalUser.isPresent()) {
                return byExternalUser;
            }
        }
        if (mobile != null && !mobile.isBlank()) {
            return queryConversionCandidate("mobile = ?", mobile.trim());
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<LeadRow> queryConversionCandidate(String condition, String value) {
        return jdbcTemplate.query(
                """
                select id, lead_no, name, mobile, source_channel, source_code, intent_course_id,
                       owner_user_id, wecom_external_user_id, status, match_exception_flag,
                       next_follow_at, latest_follow_at, student_id, converted_order_id, abandon_reason
                from crm_lead
                where deleted_flag = 0
                  and status <> 'CONVERTED'
                  and %s
                order by coalesce(latest_follow_at, created_at) desc, id desc
                limit 1
                """.formatted(condition),
                (rs, rowNum) -> mapLead(rs),
                value)
            .stream()
            .findFirst();
    }

    private void ensureLeadWritable(AdminPrincipal principal, LeadRow lead) {
        if (!isOpsOnly(principal)) {
            return;
        }
        if (lead.ownerUserId() == null || lead.ownerUserId() != principal.userId()) {
            auditLogService.writeFailure(
                principal,
                "CRM",
                "DATA_SCOPE_DENIED",
                "CRM_LEAD",
                lead.id(),
                lead.leadNo(),
                null,
                "运营只能处理本人负责线索");
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权处理非本人负责线索");
        }
    }

    private boolean isOpsOnly(AdminPrincipal principal) {
        return principal.roleCodes().contains("OPS")
            && !"ALL".equals(principal.permissionView().dataScope().scopeCode());
    }

    private void validateMobile(String mobile) {
        if (mobile == null || !mobile.matches("1[3-9]\\d{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "手机号格式错误");
        }
    }

    private AdminPrincipal currentPrincipal() {
        AdminPrincipal principal = AdminPrincipalContext.currentOrNull();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少登录态");
        }
        return principal;
    }

    private LeadRow mapLead(ResultSet rs) throws SQLException {
        return new LeadRow(
            rs.getLong("id"),
            rs.getString("lead_no"),
            rs.getString("name"),
            rs.getString("mobile"),
            rs.getString("source_channel"),
            rs.getString("source_code"),
            nullableLong(rs, "intent_course_id"),
            nullableLong(rs, "owner_user_id"),
            rs.getString("wecom_external_user_id"),
            rs.getString("status"),
            rs.getInt("match_exception_flag") == 1,
            rs.getObject("next_follow_at", LocalDateTime.class),
            rs.getObject("latest_follow_at", LocalDateTime.class),
            nullableLong(rs, "student_id"),
            nullableLong(rs, "converted_order_id"),
            rs.getString("abandon_reason"));
    }

    private LeadResponse toLeadResponse(LeadRow lead) {
        return new LeadResponse(
            lead.id(),
            lead.leadNo(),
            lead.name(),
            lead.mobile(),
            lead.sourceChannel(),
            lead.sourceCode(),
            lead.intentCourseId(),
            lead.ownerUserId(),
            lead.wecomExternalUserId(),
            lead.status(),
            lead.matchExceptionFlag(),
            lead.nextFollowAt(),
            lead.latestFollowAt(),
            lead.studentId(),
            lead.convertedOrderId(),
            lead.abandonReason());
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private record LeadRow(
        long id,
        String leadNo,
        String name,
        String mobile,
        String sourceChannel,
        String sourceCode,
        Long intentCourseId,
        Long ownerUserId,
        String wecomExternalUserId,
        String status,
        boolean matchExceptionFlag,
        LocalDateTime nextFollowAt,
        LocalDateTime latestFollowAt,
        Long studentId,
        Long convertedOrderId,
        String abandonReason
    ) {
    }

    public record PublicLeadCommand(String name, String mobile, String sourceCode, Long intentCourseId) {
    }

    public record LeadSaveCommand(
        Long leadId,
        String name,
        String mobile,
        String sourceChannel,
        String sourceCode,
        Long intentCourseId,
        Long ownerUserId,
        String wecomExternalUserId,
        LocalDateTime nextFollowAt,
        String remark
    ) {
    }

    public record FollowRecordCommand(String followMethod, String content, LocalDateTime nextFollowAt) {
    }

    public record LeadStatusCommand(String targetStatus, String abandonReason, LocalDateTime nextFollowAt) {
    }

    public record LeadResponse(
        long leadId,
        String leadNo,
        String name,
        String mobile,
        String sourceChannel,
        String sourceCode,
        Long intentCourseId,
        Long ownerUserId,
        String wecomExternalUserId,
        String status,
        boolean matchExceptionFlag,
        LocalDateTime nextFollowAt,
        LocalDateTime latestFollowAt,
        Long studentId,
        Long convertedOrderId,
        String abandonReason
    ) {
    }

    public record FollowRecordResponse(
        long followRecordId,
        long leadId,
        String followMethod,
        String content,
        LocalDateTime nextFollowAt,
        String status
    ) {
    }

    public record LeadPage(List<LeadResponse> records, int pageNo, int pageSize, int total) {
    }

    public record LeadPaidConversionCommand(
        Long studentId,
        String mobile,
        String wecomExternalUserId,
        Long orderId,
        LocalDateTime paidAt
    ) {
    }

    public record LeadPaidConversionResponse(
        boolean matched,
        Long leadId,
        String leadNo,
        String status,
        Long studentId,
        Long orderId,
        boolean idempotentHit,
        String missReason
    ) {
    }
}
