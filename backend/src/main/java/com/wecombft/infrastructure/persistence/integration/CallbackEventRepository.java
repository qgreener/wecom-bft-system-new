package com.wecombft.infrastructure.persistence.integration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CallbackEventRepository {

    private static final String PENDING = "PENDING";
    private static final String PROCESSED = "PROCESSED";
    private static final String FAILED = "FAILED";

    private final JdbcTemplate jdbcTemplate;

    public CallbackEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CallbackEventRecord recordReceived(CallbackEventCommand command) {
        try {
            jdbcTemplate.update(
                    """
                    insert into integration_callback_event (
                        id, event_no, source_system, event_type, idempotency_key, order_id,
                        related_object_type, related_object_id, related_object_no,
                        processing_status, retry_count, raw_snapshot, received_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                    """,
                    command.id(),
                    command.eventNo(),
                    command.sourceSystem(),
                    command.eventType(),
                    command.idempotencyKey(),
                    command.orderId(),
                    command.relatedObjectType(),
                    command.relatedObjectId(),
                    command.relatedObjectNo(),
                    PENDING,
                    command.rawSnapshot(),
                    LocalDateTime.now());
        } catch (DuplicateKeyException duplicateKeyException) {
            return findBySourceAndEventNo(command.sourceSystem(), command.eventNo())
                    .or(() -> findByIdempotencyKey(command.idempotencyKey()))
                    .orElseThrow(() -> duplicateKeyException);
        }
        return findBySourceAndEventNo(command.sourceSystem(), command.eventNo()).orElseThrow();
    }

    public Optional<CallbackEventRecord> findBySourceAndEventNo(String sourceSystem, String eventNo) {
        return jdbcTemplate.query(
                """
                select id, event_no, source_system, event_type, idempotency_key, order_id,
                       related_object_type, related_object_id, related_object_no,
                       processing_status, retry_count, failure_reason, raw_snapshot, received_at, processed_at
                from integration_callback_event
                where source_system = ? and event_no = ?
                """,
                this::mapRecord,
                sourceSystem,
                eventNo)
            .stream()
            .findFirst();
    }

    public void markProcessed(long id) {
        jdbcTemplate.update(
                """
                update integration_callback_event
                set processing_status = ?,
                    failure_reason = null,
                    processed_at = ?
                where id = ?
                """,
                PROCESSED,
                LocalDateTime.now(),
                id);
    }

    public void markFailed(long id, String failureReason) {
        jdbcTemplate.update(
                """
                update integration_callback_event
                set processing_status = ?,
                    retry_count = retry_count + 1,
                    failure_reason = ?
                where id = ?
                """,
                FAILED,
                failureReason,
                id);
    }

    private Optional<CallbackEventRecord> findByIdempotencyKey(String idempotencyKey) {
        List<CallbackEventRecord> records = jdbcTemplate.query(
                """
                select id, event_no, source_system, event_type, idempotency_key, order_id,
                       related_object_type, related_object_id, related_object_no,
                       processing_status, retry_count, failure_reason, raw_snapshot, received_at, processed_at
                from integration_callback_event
                where idempotency_key = ?
                """,
                this::mapRecord,
                idempotencyKey);
        return records.stream().findFirst();
    }

    private CallbackEventRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new CallbackEventRecord(
                resultSet.getLong("id"),
                resultSet.getString("event_no"),
                resultSet.getString("source_system"),
                resultSet.getString("event_type"),
                resultSet.getString("idempotency_key"),
                nullableLong(resultSet, "order_id"),
                resultSet.getString("related_object_type"),
                nullableLong(resultSet, "related_object_id"),
                resultSet.getString("related_object_no"),
                resultSet.getString("processing_status"),
                resultSet.getInt("retry_count"),
                resultSet.getString("failure_reason"),
                resultSet.getString("raw_snapshot"),
                resultSet.getObject("received_at", LocalDateTime.class),
                resultSet.getObject("processed_at", LocalDateTime.class));
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public record CallbackEventCommand(
            long id,
            String eventNo,
            String sourceSystem,
            String eventType,
            String idempotencyKey,
            Long orderId,
            String relatedObjectType,
            Long relatedObjectId,
            String relatedObjectNo,
            String rawSnapshot) {
    }
}
