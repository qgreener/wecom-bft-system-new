package com.wecombft.infrastructure.persistence.iam;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class IamRepository {

    private final JdbcTemplate jdbcTemplate;

    public IamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserRecord> findActiveUserByUserNo(String userNo) {
        return jdbcTemplate.query(
                """
                select id, user_no, display_name, status
                from sys_user
                where user_no = ? and user_type = 'INTERNAL' and status = 'ACTIVE' and deleted_flag = 0
                """,
                userMapper(),
                userNo)
            .stream()
            .findFirst();
    }

    public List<RoleRecord> findActiveRolesByUserId(long userId) {
        return jdbcTemplate.query(
            """
            select r.id, r.role_code, r.role_name, r.data_scope
            from sys_user_role ur
            join sys_role r on r.id = ur.role_id
            where ur.user_id = ?
              and ur.grant_status = 'ACTIVE'
              and r.status = 'ACTIVE'
              and r.deleted_flag = 0
            order by r.sort_no, r.id
            """,
            roleMapper(),
            userId);
    }

    public Optional<RoleRecord> findActiveRoleByCode(String roleCode) {
        return jdbcTemplate.query(
                """
                select id, role_code, role_name, data_scope
                from sys_role
                where role_code = ? and status = 'ACTIVE' and deleted_flag = 0
                """,
                roleMapper(),
                roleCode)
            .stream()
            .findFirst();
    }

    public Optional<ApprovalRecord> findPendingRoleApplication(long applicantUserId, String roleCode) {
        return jdbcTemplate.query(
                """
                select id, approval_no, approval_type, title, applicant_user_id, approver_user_id,
                       related_object_no, status, submit_reason, approval_comment, submitted_at, finished_at
                from approval_record
                where applicant_user_id = ?
                  and approval_type = 'ROLE_APPLICATION'
                  and related_object_no = ?
                  and status = 'PENDING'
                order by submitted_at desc
                limit 1
                """,
                approvalMapper(),
                applicantUserId,
                roleCode)
            .stream()
            .findFirst();
    }

    public Optional<ApprovalRecord> findApprovalById(long approvalId) {
        return jdbcTemplate.query(
                """
                select id, approval_no, approval_type, title, applicant_user_id, approver_user_id,
                       related_object_no, status, submit_reason, approval_comment, submitted_at, finished_at
                from approval_record
                where id = ?
                """,
                approvalMapper(),
                approvalId)
            .stream()
            .findFirst();
    }

    public List<ApprovalRecord> findApprovals(String approvalType, String status) {
        StringBuilder sql = new StringBuilder(
            """
            select id, approval_no, approval_type, title, applicant_user_id, approver_user_id,
                   related_object_no, status, submit_reason, approval_comment, submitted_at, finished_at
            from approval_record
            where 1 = 1
            """);
        List<Object> args = new java.util.ArrayList<>();
        appendEquals(sql, args, "approval_type", approvalType);
        appendEquals(sql, args, "status", status);
        sql.append(" order by submitted_at desc, id desc");
        return jdbcTemplate.query(sql.toString(), approvalMapper(), args.toArray());
    }

    public ApprovalRecord insertRoleApplication(
        long approvalId,
        String approvalNo,
        UserRecord applicant,
        RoleRecord role,
        String submitReason
    ) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
            """
            insert into approval_record (
                id, approval_no, approval_type, title, applicant_user_id, approver_user_id,
                related_object_type, related_object_id, related_object_no, status, submit_reason,
                submitted_at, created_by, updated_by
            ) values (?, ?, 'ROLE_APPLICATION', ?, ?, null, 'SYS_ROLE', ?, ?, 'PENDING', ?, ?, ?, ?)
            """,
            approvalId,
            approvalNo,
            "申请角色：" + role.roleName(),
            applicant.id(),
            role.id(),
            role.roleCode(),
            submitReason,
            now,
            applicant.id(),
            applicant.id());
        return findApprovalById(approvalId).orElseThrow();
    }

    public ApprovalRecord finishApproval(long approvalId, long approverUserId, String status, String comment) {
        jdbcTemplate.update(
            """
            update approval_record
            set status = ?,
                approver_user_id = ?,
                approval_comment = ?,
                finished_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'PENDING'
            """,
            status,
            approverUserId,
            comment,
            LocalDateTime.now(),
            approverUserId,
            approvalId);
        return findApprovalById(approvalId).orElseThrow();
    }

    public void grantRole(long grantId, long userId, long roleId, long grantedBy) {
        Integer existing = jdbcTemplate.queryForObject(
            """
            select count(*) from sys_user_role
            where user_id = ? and role_id = ?
            """,
            Integer.class,
            userId,
            roleId);
        if (existing != null && existing > 0) {
            jdbcTemplate.update(
                """
                update sys_user_role
                set grant_status = 'ACTIVE',
                    granted_by = ?,
                    granted_at = ?,
                    revoked_by = null,
                    revoked_at = null,
                    updated_by = ?,
                    version = version + 1
                where user_id = ? and role_id = ?
                """,
                grantedBy,
                LocalDateTime.now(),
                grantedBy,
                userId,
                roleId);
            return;
        }

        jdbcTemplate.update(
            """
            insert into sys_user_role (
                id, user_id, role_id, grant_status, granted_by, granted_at, created_by, updated_by
            ) values (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
            """,
            grantId,
            userId,
            roleId,
            grantedBy,
            LocalDateTime.now(),
            grantedBy,
            grantedBy);
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String columnName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" and ").append(columnName).append(" = ?");
        args.add(value);
    }

    private RowMapper<UserRecord> userMapper() {
        return (resultSet, rowNum) -> new UserRecord(
            resultSet.getLong("id"),
            resultSet.getString("user_no"),
            resultSet.getString("display_name"),
            resultSet.getString("status"));
    }

    private RowMapper<RoleRecord> roleMapper() {
        return (resultSet, rowNum) -> new RoleRecord(
            resultSet.getLong("id"),
            resultSet.getString("role_code"),
            resultSet.getString("role_name"),
            resultSet.getString("data_scope"));
    }

    private RowMapper<ApprovalRecord> approvalMapper() {
        return (resultSet, rowNum) -> mapApproval(resultSet);
    }

    private ApprovalRecord mapApproval(ResultSet resultSet) throws SQLException {
        return new ApprovalRecord(
            resultSet.getLong("id"),
            resultSet.getString("approval_no"),
            resultSet.getString("approval_type"),
            resultSet.getString("title"),
            resultSet.getLong("applicant_user_id"),
            nullableLong(resultSet, "approver_user_id"),
            resultSet.getString("related_object_no"),
            resultSet.getString("status"),
            resultSet.getString("submit_reason"),
            resultSet.getString("approval_comment"),
            resultSet.getObject("submitted_at", LocalDateTime.class),
            resultSet.getObject("finished_at", LocalDateTime.class));
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
