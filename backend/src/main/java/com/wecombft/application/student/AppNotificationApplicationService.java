package com.wecombft.application.student;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.shared.web.ApiException;

@Service
public class AppNotificationApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final AppStudentApplicationService appStudentApplicationService;

    public AppNotificationApplicationService(
        JdbcTemplate jdbcTemplate,
        AppStudentApplicationService appStudentApplicationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.appStudentApplicationService = appStudentApplicationService;
    }

    public List<NotificationView> listMine(String authorizationHeader, Integer pageNo, Integer pageSize) {
        long studentId = appStudentApplicationService.requireStudent(authorizationHeader).studentId();
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        int offset = (page - 1) * size;
        return jdbcTemplate.query(
            """
            select id, notification_no, channel, scene_code, title, content, order_id,
                   related_object_type, related_object_id, send_status, read_status,
                   sent_at, read_at, created_at
            from notify_message
            where receiver_student_id = ? and channel = 'IN_APP'
            order by created_at desc, id desc
            limit ? offset ?
            """,
            this::mapNotification,
            studentId, size, offset);
    }

    @Transactional
    public NotificationView markRead(String authorizationHeader, long notificationId) {
        long studentId = appStudentApplicationService.requireStudent(authorizationHeader).studentId();
        int rows = jdbcTemplate.update(
            """
            update notify_message
            set read_status = 'READ', read_at = ?, updated_at = ?, version = version + 1
            where id = ? and receiver_student_id = ? and channel = 'IN_APP'
            """,
            LocalDateTime.now(),
            LocalDateTime.now(),
            notificationId,
            studentId);
        if (rows == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "通知不存在或无权访问");
        }
        return findById(notificationId);
    }

    private NotificationView findById(long id) {
        List<NotificationView> list = jdbcTemplate.query(
            """
            select id, notification_no, channel, scene_code, title, content, order_id,
                   related_object_type, related_object_id, send_status, read_status,
                   sent_at, read_at, created_at
            from notify_message where id = ?
            """,
            this::mapNotification, id);
        if (list.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "通知不存在");
        }
        return list.get(0);
    }

    private NotificationView mapNotification(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationView(
            rs.getLong("id"),
            rs.getString("notification_no"),
            rs.getString("channel"),
            rs.getString("scene_code"),
            rs.getString("title"),
            rs.getString("content"),
            (Long) rs.getObject("order_id"),
            rs.getString("related_object_type"),
            (Long) rs.getObject("related_object_id"),
            rs.getString("send_status"),
            rs.getString("read_status"),
            rs.getObject("sent_at", LocalDateTime.class),
            rs.getObject("read_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class));
    }

    public record NotificationView(
        long id,
        String notificationNo,
        String channel,
        String sceneCode,
        String title,
        String content,
        Long orderId,
        String relatedObjectType,
        Long relatedObjectId,
        String sendStatus,
        String readStatus,
        LocalDateTime sentAt,
        LocalDateTime readAt,
        LocalDateTime createdAt
    ) {
    }
}
