package com.wecombft.infrastructure.persistence.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(AuditWriteCommand command) {
        jdbcTemplate.update(
            """
            insert into audit_operation_log (
                id, trace_id, operator_user_id, operator_name, operation_module, operation_type,
                target_type, target_id, target_no, order_id, before_snapshot, after_snapshot,
                result, failure_reason, client_ip, user_agent, occurred_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            command.id(),
            command.traceId(),
            command.operatorUserId(),
            command.operatorName(),
            command.operationModule(),
            command.operationType(),
            command.targetType(),
            command.targetId(),
            command.targetNo(),
            command.orderId(),
            command.beforeSnapshot(),
            command.afterSnapshot(),
            command.result(),
            command.failureReason(),
            command.clientIp(),
            command.userAgent(),
            command.occurredAt());
    }

    public List<AuditLogRecord> search(AuditQuery query) {
        StringBuilder sql = new StringBuilder(
            """
            select id, trace_id, operator_user_id, operator_name, operation_module, operation_type,
                   target_type, target_id, target_no, order_id, result, failure_reason, occurred_at
            from audit_operation_log
            where 1 = 1
            """);
        List<Object> args = new ArrayList<>();

        appendEquals(sql, args, "trace_id", query.traceId());
        appendEquals(sql, args, "operation_module", query.operationModule());
        appendEquals(sql, args, "operation_type", query.operationType());
        appendEquals(sql, args, "target_type", query.targetType());
        appendEquals(sql, args, "target_id", query.targetId());
        appendEquals(sql, args, "order_id", query.orderId());
        appendEquals(sql, args, "operator_user_id", query.operatorUserId());

        sql.append(" order by occurred_at desc, id desc limit ? offset ?");
        args.add(query.pageSize());
        args.add((query.pageNo() - 1) * query.pageSize());

        return jdbcTemplate.query(sql.toString(), this::mapLog, args.toArray());
    }

    public int count(AuditQuery query) {
        StringBuilder sql = new StringBuilder("select count(*) from audit_operation_log where 1 = 1");
        List<Object> args = new ArrayList<>();
        appendEquals(sql, args, "trace_id", query.traceId());
        appendEquals(sql, args, "operation_module", query.operationModule());
        appendEquals(sql, args, "operation_type", query.operationType());
        appendEquals(sql, args, "target_type", query.targetType());
        appendEquals(sql, args, "target_id", query.targetId());
        appendEquals(sql, args, "order_id", query.orderId());
        appendEquals(sql, args, "operator_user_id", query.operatorUserId());
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    private void appendEquals(StringBuilder sql, List<Object> args, String columnName, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        sql.append(" and ").append(columnName).append(" = ?");
        args.add(value);
    }

    private AuditLogRecord mapLog(ResultSet resultSet, int rowNum) throws SQLException {
        return new AuditLogRecord(
            resultSet.getLong("id"),
            resultSet.getString("trace_id"),
            nullableLong(resultSet, "operator_user_id"),
            resultSet.getString("operator_name"),
            resultSet.getString("operation_module"),
            resultSet.getString("operation_type"),
            resultSet.getString("target_type"),
            nullableLong(resultSet, "target_id"),
            resultSet.getString("target_no"),
            nullableLong(resultSet, "order_id"),
            resultSet.getString("result"),
            resultSet.getString("failure_reason"),
            resultSet.getObject("occurred_at", LocalDateTime.class));
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public record AuditWriteCommand(
        long id,
        String traceId,
        Long operatorUserId,
        String operatorName,
        String operationModule,
        String operationType,
        String targetType,
        Long targetId,
        String targetNo,
        Long orderId,
        String beforeSnapshot,
        String afterSnapshot,
        String result,
        String failureReason,
        String clientIp,
        String userAgent,
        LocalDateTime occurredAt
    ) {
    }

    public record AuditQuery(
        String traceId,
        String operationModule,
        String operationType,
        String targetType,
        Long targetId,
        Long orderId,
        Long operatorUserId,
        int pageNo,
        int pageSize
    ) {
    }
}
