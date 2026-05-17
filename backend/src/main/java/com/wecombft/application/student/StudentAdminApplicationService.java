package com.wecombft.application.student;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.interfaces.dto.student.StudentAdminDetailResponse;
import com.wecombft.interfaces.dto.student.StudentAdminListItem;
import com.wecombft.interfaces.dto.student.StudentAdminPage;
import com.wecombft.shared.web.ApiException;

@Service
public class StudentAdminApplicationService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final JdbcTemplate jdbcTemplate;

    public StudentAdminApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StudentAdminPage adminStudents(
        AdminPrincipal principal,
        String keyword,
        String status,
        Integer pageNo,
        Integer pageSize
    ) {
        requireAdmin(principal);
        List<Object> args = new ArrayList<>();
        StringBuilder condition = new StringBuilder("s.deleted_flag = 0");
        if (keyword != null && !keyword.isBlank()) {
            condition.append(" and (s.student_no like ? or s.mobile like ? or s.nickname like ? or s.real_name like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            List<String> allowed = List.of("ACTIVE", "MERGED", "INACTIVE");
            if (!allowed.contains(status)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "学员状态非法");
            }
            condition.append(" and s.status = ?");
            args.add(status);
        }
        Integer total = jdbcTemplate.queryForObject(
            "select count(*) from edu_student s where " + condition,
            Integer.class,
            args.toArray());

        int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? DEFAULT_PAGE_NO : Math.max(1, pageNo);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);

        boolean canSeeMobileFull = canSeeMobileFull(principal);
        boolean canSeeMobileTail = canSeeMobileTail(principal);

        List<StudentAdminListItem> records = jdbcTemplate.query(
            """
            select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.real_name, s.status,
                   s.primary_lead_id, s.merged_to_student_id, s.created_at
            from edu_student s
            where %s
            order by s.created_at desc, s.id desc
            limit ? offset ?
            """.formatted(condition),
            (rs, rowNum) -> new StudentAdminListItem(
                rs.getLong("id"),
                rs.getString("student_no"),
                nullableLong(rs, "user_id"),
                maskMobile(rs.getString("mobile"), canSeeMobileFull, canSeeMobileTail),
                rs.getString("nickname"),
                rs.getString("real_name"),
                rs.getString("status"),
                nullableLong(rs, "primary_lead_id"),
                nullableLong(rs, "merged_to_student_id"),
                toLocalDateTime(rs, "created_at")),
            queryArgs.toArray());

        return new StudentAdminPage(records, page, size, total == null ? 0 : total);
    }

    public StudentAdminDetailResponse adminStudentDetail(AdminPrincipal principal, long studentId) {
        requireAdmin(principal);
        boolean canSeeMobileFull = canSeeMobileFull(principal);
        boolean canSeeMobileTail = canSeeMobileTail(principal);
        return jdbcTemplate.query(
                """
                select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.real_name,
                       s.wx_openid, s.wx_unionid, s.wecom_external_user_id, s.status,
                       s.primary_lead_id, s.merged_to_student_id, s.tags,
                       s.created_at, s.updated_at,
                       coalesce(u.user_no, '') as user_no
                from edu_student s
                left join sys_user u on u.id = s.user_id
                where s.id = ? and s.deleted_flag = 0
                """,
                (rs, rowNum) -> new StudentAdminDetailResponse(
                    rs.getLong("id"),
                    rs.getString("student_no"),
                    nullableLong(rs, "user_id"),
                    rs.getString("user_no"),
                    maskMobile(rs.getString("mobile"), canSeeMobileFull, canSeeMobileTail),
                    rs.getString("nickname"),
                    rs.getString("real_name"),
                    rs.getString("wx_openid"),
                    rs.getString("wx_unionid"),
                    rs.getString("wecom_external_user_id"),
                    rs.getString("status"),
                    nullableLong(rs, "primary_lead_id"),
                    nullableLong(rs, "merged_to_student_id"),
                    rs.getString("tags"),
                    toLocalDateTime(rs, "created_at"),
                    toLocalDateTime(rs, "updated_at")),
                studentId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "学员档案不存在"));
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
    }

    private boolean canSeeMobileFull(AdminPrincipal principal) {
        List<String> roles = principal.roleCodes();
        return roles.contains("SUPER_ADMIN") || roles.contains("EDU_ADMIN");
    }

    private boolean canSeeMobileTail(AdminPrincipal principal) {
        return principal.roleCodes().contains("SERVICE");
    }

    static String maskMobile(String mobile, boolean canSeeFull, boolean canSeeTail) {
        if (mobile == null || mobile.isBlank()) {
            return mobile;
        }
        if (canSeeFull) {
            return mobile;
        }
        if (canSeeTail && mobile.length() >= 7) {
            return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
        }
        return "*".repeat(Math.min(mobile.length(), 11));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }
}
