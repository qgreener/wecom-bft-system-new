package com.wecombft.infrastructure.job;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.notification.NotificationContent;
import com.wecombft.application.notification.NotificationDispatchService;

/**
 * 定时扫描进入「待删除」状态且已设置 delete_notice_deadline 的课程：
 * - 每次执行：找出 ACTIVE 学习权益对应的学员，发送一条课程待删除站内通知
 *   （幂等键 = course_no + student_id + 'COURSE_DELETE_NOTICE'）
 * - 当 delete_notice_deadline <= now() 时，将课程置为逻辑删除（DELETED）
 *
 * 设计依据：02-业务流程与状态机说明.md / 01-需求规格说明书 §8.2 课程上架与学习
 */
@Component
public class DeleteCourseNoticeScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeleteCourseNoticeScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationDispatchService notificationDispatchService;

    public DeleteCourseNoticeScheduler(
        JdbcTemplate jdbcTemplate,
        NotificationDispatchService notificationDispatchService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationDispatchService = notificationDispatchService;
    }

    // 每 30 分钟扫一次；演示环境也可手动触发
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void tick() {
        List<PendingCourse> pending = jdbcTemplate.query(
            """
            select id, course_no, course_title, delete_notice_deadline
            from course
            where status = 'DELETE_PENDING' and deleted_flag = 0
              and delete_notice_deadline is not null
            """,
            (rs, rowNum) -> mapPendingCourse(rs));
        if (pending.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PendingCourse course : pending) {
            try {
                notifyActiveEntitlements(course, now);
                if (course.deadline() != null && !course.deadline().isAfter(now)) {
                    softDeleteCourse(course);
                }
            } catch (Exception e) {
                log.warn("DELETE_COURSE_NOTICE 课程 {} 处理失败：{}", course.courseNo(), e.getMessage());
            }
        }
    }

    private void notifyActiveEntitlements(PendingCourse course, LocalDateTime now) {
        List<ActiveEntitlement> students = jdbcTemplate.query(
            """
            select student_id from learning_entitlement
            where course_id = ? and status = 'ACTIVE'
            """,
            (rs, rowNum) -> new ActiveEntitlement(rs.getLong("student_id")),
            course.courseId());
        for (ActiveEntitlement target : students) {
            String idempotencyKey = "COURSE_DELETE_NOTICE_" + course.courseNo() + "_" + target.studentId();
            String title = "课程待删除提醒";
            String content = "课程「" + course.courseTitle() + "」将在 " + course.deadline() + " 删除，请尽快完成学习。";
            try {
                notificationDispatchService.dispatchToStudent(
                    target.studentId(),
                    new NotificationContent(
                        "COURSE_DELETE_NOTICE",
                        null,
                        title,
                        content,
                        "COURSE",
                        course.courseId(),
                        idempotencyKey,
                        null,
                        null));
            } catch (Exception e) {
                // 通知失败不影响课程删除；幂等键保证重复尝试不重复入库
                log.debug("课程 {} 学员 {} 通知失败：{}", course.courseNo(), target.studentId(), e.getMessage());
            }
        }
    }

    private void softDeleteCourse(PendingCourse course) {
        int rows = jdbcTemplate.update(
            """
            update course
            set status = 'DELETED', deleted_flag = 1, deleted_at = ?, updated_at = ?, version = version + 1
            where id = ? and status = 'DELETE_PENDING' and deleted_flag = 0
            """,
            LocalDateTime.now(),
            LocalDateTime.now(),
            course.courseId());
        if (rows > 0) {
            log.info("课程 {} 已自动逻辑删除（deadline={}）", course.courseNo(), course.deadline());
        }
    }

    private PendingCourse mapPendingCourse(ResultSet rs) throws SQLException {
        return new PendingCourse(
            rs.getLong("id"),
            rs.getString("course_no"),
            rs.getString("course_title"),
            rs.getObject("delete_notice_deadline", LocalDateTime.class));
    }

    private record PendingCourse(long courseId, String courseNo, String courseTitle, LocalDateTime deadline) {
    }

    private record ActiveEntitlement(long studentId) {
    }
}
