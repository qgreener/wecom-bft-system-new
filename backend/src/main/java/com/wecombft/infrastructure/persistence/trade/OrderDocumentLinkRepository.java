package com.wecombft.infrastructure.persistence.trade;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderDocumentLinkRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderDocumentLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrderDocumentLinkRecord upsert(DocumentLinkCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        try {
            insert(command);
        } catch (DuplicateKeyException duplicateKeyException) {
            updateExisting(command);
        }
        return findByOrderAndDocument(command.orderId(), command.documentType(), command.documentId()).orElseThrow();
    }

    public void upsertAll(List<DocumentLinkCommand> commands) {
        Objects.requireNonNull(commands, "commands must not be null");
        commands.forEach(this::upsert);
    }

    public List<OrderDocumentLinkRecord> findByOrderId(long orderId) {
        return jdbcTemplate.query(
                """
                select id, order_id, order_no, document_type, document_id, document_no,
                       document_status, amount_cent, relation_type, occurred_at, source_table, remark
                from order_document_link
                where order_id = ?
                order by occurred_at, id
                """,
                this::mapRecord,
                orderId);
    }

    private void insert(DocumentLinkCommand command) {
        jdbcTemplate.update(
                """
                insert into order_document_link (
                    id, order_id, order_no, document_type, document_id, document_no,
                    document_status, amount_cent, relation_type, occurred_at, source_table,
                    remark, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                command.id(),
                command.orderId(),
                command.orderNo(),
                command.documentType(),
                command.documentId(),
                command.documentNo(),
                command.documentStatus(),
                command.amountCent(),
                command.relationType(),
                command.occurredAt(),
                command.sourceTable(),
                command.remark(),
                command.operatorUserId(),
                command.operatorUserId());
    }

    private void updateExisting(DocumentLinkCommand command) {
        jdbcTemplate.update(
                """
                update order_document_link
                set order_no = ?,
                    document_no = ?,
                    document_status = ?,
                    amount_cent = ?,
                    relation_type = ?,
                    occurred_at = ?,
                    source_table = ?,
                    remark = ?,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                where order_id = ? and document_type = ? and document_id = ?
                """,
                command.orderNo(),
                command.documentNo(),
                command.documentStatus(),
                command.amountCent(),
                command.relationType(),
                command.occurredAt(),
                command.sourceTable(),
                command.remark(),
                LocalDateTime.now(),
                command.operatorUserId(),
                command.orderId(),
                command.documentType(),
                command.documentId());
    }

    private Optional<OrderDocumentLinkRecord> findByOrderAndDocument(
            long orderId,
            String documentType,
            long documentId) {
        return jdbcTemplate.query(
                """
                select id, order_id, order_no, document_type, document_id, document_no,
                       document_status, amount_cent, relation_type, occurred_at, source_table, remark
                from order_document_link
                where order_id = ? and document_type = ? and document_id = ?
                """,
                this::mapRecord,
                orderId,
                documentType,
                documentId)
            .stream()
            .findFirst();
    }

    private OrderDocumentLinkRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new OrderDocumentLinkRecord(
                resultSet.getLong("id"),
                resultSet.getLong("order_id"),
                resultSet.getString("order_no"),
                resultSet.getString("document_type"),
                resultSet.getLong("document_id"),
                resultSet.getString("document_no"),
                resultSet.getString("document_status"),
                nullableLong(resultSet, "amount_cent"),
                resultSet.getString("relation_type"),
                resultSet.getObject("occurred_at", LocalDateTime.class),
                resultSet.getString("source_table"),
                resultSet.getString("remark"));
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public record DocumentLinkCommand(
            long id,
            long orderId,
            String orderNo,
            String documentType,
            long documentId,
            String documentNo,
            String documentStatus,
            Long amountCent,
            String relationType,
            LocalDateTime occurredAt,
            String sourceTable,
            String remark,
            Long operatorUserId) {
    }
}
