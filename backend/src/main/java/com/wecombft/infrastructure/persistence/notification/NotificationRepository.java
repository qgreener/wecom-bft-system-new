package com.wecombft.infrastructure.persistence.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Long> findActiveUserIdsByRoleCode(String roleCode) {
        return jdbcTemplate.queryForList(
            """
            select u.id
            from sys_user u
            join sys_user_role ur on ur.user_id = u.id and ur.grant_status = 'ACTIVE'
            join sys_role r on r.id = ur.role_id and r.status = 'ACTIVE' and r.deleted_flag = 0
            where r.role_code = ?
              and u.status = 'ACTIVE'
              and u.deleted_flag = 0
            order by u.id
            """,
            Long.class,
            roleCode);
    }

    public void insertIfAbsent(NotificationWriteCommand command) {
        Integer existing = jdbcTemplate.queryForObject(
            """
            select count(*) from notify_message
            where idempotency_key = ?
              and channel = ?
              and receiver_user_id = ?
            """,
            Integer.class,
            command.idempotencyKey(),
            command.channel(),
            command.receiverUserId());
        if (existing != null && existing > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
            """
            insert into notify_message (
                id, notification_no, receiver_user_id, receiver_student_id, channel,
                scene_code, template_code, title, content, order_id,
                related_object_type, related_object_id, send_status, read_status,
                sent_at, read_at, failure_reason, retry_count, idempotency_key,
                created_at, created_by, updated_by
            ) values (?, ?, ?, null, ?, ?, ?, ?, ?, null, ?, ?, 'PENDING', 'UNREAD',
                null, null, null, 0, ?, ?, ?, ?)
            """,
            command.id(),
            command.notificationNo(),
            command.receiverUserId(),
            command.channel(),
            command.sceneCode(),
            command.templateCode(),
            command.title(),
            command.content(),
            command.relatedObjectType(),
            command.relatedObjectId(),
            command.idempotencyKey(),
            now,
            command.createdBy(),
            command.createdBy());
    }

    public List<NotificationRecord> search(NotificationQuery query) {
        StringBuilder sql = new StringBuilder(
            """
            select id, notification_no, receiver_user_id, channel, scene_code, template_code,
                   title, content, related_object_type, related_object_id,
                   send_status, read_status, created_at
            from notify_message
            where 1 = 1
            """);
        List<Object> args = new ArrayList<>();
        appendEquals(sql, args, "scene_code", query.sceneCode());
        appendEquals(sql, args, "receiver_user_id", query.receiverUserId());
        sql.append(" order by created_at desc, id desc");
        return jdbcTemplate.query(sql.toString(), this::mapNotification, args.toArray());
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

    private NotificationRecord mapNotification(ResultSet resultSet, int rowNum) throws SQLException {
        return new NotificationRecord(
            resultSet.getLong("id"),
            resultSet.getString("notification_no"),
            nullableLong(resultSet, "receiver_user_id"),
            resultSet.getString("channel"),
            resultSet.getString("scene_code"),
            resultSet.getString("template_code"),
            resultSet.getString("title"),
            resultSet.getString("content"),
            resultSet.getString("related_object_type"),
            nullableLong(resultSet, "related_object_id"),
            resultSet.getString("send_status"),
            resultSet.getString("read_status"),
            resultSet.getObject("created_at", LocalDateTime.class));
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }

    public record NotificationWriteCommand(
        long id,
        String notificationNo,
        Long receiverUserId,
        String channel,
        String sceneCode,
        String templateCode,
        String title,
        String content,
        String relatedObjectType,
        Long relatedObjectId,
        String idempotencyKey,
        Long createdBy
    ) {
    }

    public record NotificationQuery(String sceneCode, Long receiverUserId) {
    }
}
