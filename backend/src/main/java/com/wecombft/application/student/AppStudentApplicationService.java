package com.wecombft.application.student;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class AppStudentApplicationService {

    private static final String TOKEN_PREFIX = "S3-APP-DEMO-";

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public AppStudentApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AppWechatLoginResponse wechatLogin(AppWechatLoginCommand command) {
        String wxCode = command.wxCode() == null ? "" : command.wxCode().trim();
        if (!wxCode.startsWith("mock:") || wxCode.length() == "mock:".length()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "S4 仅支持 mock 小程序登录码");
        }
        String mockIdentity = wxCode.substring("mock:".length()).trim();
        Optional<StudentSession> byUserNo = findByUserNo(mockIdentity);
        if (byUserNo.isPresent()) {
            StudentSession student = byUserNo.get();
            if (!"ACTIVE".equals(student.userStatus()) || !"ACTIVE".equals(student.status())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "学员身份不存在或已禁用");
            }
            updateLastLogin(student.userId());
            return toLoginResponse(resolveMerged(student));
        }

        String wxOpenid = mockIdentity;
        Optional<StudentSession> byOpenid = findByOpenid(wxOpenid);
        if (byOpenid.isPresent()) {
            StudentSession student = byOpenid.get();
            if (!"ACTIVE".equals(student.userStatus()) || !"ACTIVE".equals(student.status())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "学员身份不存在或已禁用");
            }
            updateLastLogin(student.userId());
            return toLoginResponse(resolveMerged(student));
        }

        long userId = idGenerator.nextId();
        long studentId = idGenerator.nextId();
        String suffix = Long.toString(Math.abs(userId % 1_000_000_000L));
        String userNo = "APP" + suffix;
        String studentNo = "STU" + suffix;
        jdbcTemplate.update(
            """
            insert into sys_user (
                id, user_no, user_type, display_name, wx_openid, status,
                last_login_at, created_by, updated_by
            ) values (?, ?, 'STUDENT', ?, ?, 'ACTIVE', ?, 0, 0)
            """,
            userId,
            userNo,
            "App Student " + suffix,
            wxOpenid,
            java.time.LocalDateTime.now());
        jdbcTemplate.update(
            """
            insert into edu_student (
                id, student_no, user_id, wx_openid, nickname, status, tags, created_by, updated_by
            ) values (?, ?, ?, ?, ?, 'ACTIVE', ?, 0, 0)
            """,
            studentId,
            studentNo,
            userId,
            wxOpenid,
            "App Student " + suffix,
            "[\"S4_MOCK\"]");
        auditLogService.writeSystemSuccess(
            "STUDENT",
            "WECHAT_LOGIN_CREATE",
            "EDU_STUDENT",
            studentId,
            studentNo,
            null,
            "{\"wx_openid\":\"" + wxOpenid + "\"}");
        return toLoginResponse(findByStudentNo(studentNo).orElseThrow());
    }

    @Transactional
    public PhoneAuthorizeResponse authorizePhone(String authorizationHeader, PhoneAuthorizeCommand command) {
        StudentSession current = requireStudent(authorizationHeader);
        String phone = parseMockPhone(command.phoneCode());
        Optional<StudentSession> existingByPhone = findActiveByMobile(phone);
        if (existingByPhone.isEmpty() || existingByPhone.get().studentId() == current.studentId()) {
            bindPhone(current, phone);
            StudentSession updated = requireStudent(authorizationHeader);
            auditLogService.writeSystemSuccess(
                "STUDENT",
                "PHONE_AUTHORIZE",
                "EDU_STUDENT",
                updated.studentId(),
                updated.studentNo(),
                null,
                "{\"mobile\":\"" + phone + "\"}");
            return new PhoneAuthorizeResponse(
                updated.studentId(),
                updated.studentNo(),
                phone,
                null,
                updated.status(),
                true);
        }

        StudentSession target = existingByPhone.get();
        if (target.wxOpenid() != null && current.wxOpenid() != null && !target.wxOpenid().equals(current.wxOpenid())) {
            auditLogService.writeSystemFailure(
                "STUDENT",
                "PHONE_MERGE_CONFLICT",
                "EDU_STUDENT",
                target.studentId(),
                target.studentNo(),
                null,
                "手机号已绑定其他 OpenID");
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "手机号已绑定其他学员，需人工核对");
        }

        jdbcTemplate.update(
            """
            update edu_student
            set status = 'MERGED',
                merged_to_student_id = ?,
                user_id = null,
                wx_openid = null,
                wx_unionid = null,
                updated_by = 0,
                version = version + 1
            where id = ?
            """,
            target.studentId(),
            current.studentId());
        jdbcTemplate.update(
            """
            update edu_student
            set user_id = ?,
                wx_openid = coalesce(wx_openid, ?),
                wx_unionid = coalesce(wx_unionid, ?),
                updated_by = 0,
                version = version + 1
            where id = ?
            """,
            current.userId(),
            current.wxOpenid(),
            current.wxUnionid(),
            target.studentId());
        jdbcTemplate.update(
            """
            update sys_user
            set mobile = ?,
                updated_by = 0,
                version = version + 1
            where id = ?
            """,
            phone,
            current.userId());
        auditLogService.writeSystemSuccess(
            "STUDENT",
            "PHONE_MERGE",
            "EDU_STUDENT",
            target.studentId(),
            target.studentNo(),
            null,
            "{\"merged_from\":\"" + current.studentNo() + "\",\"mobile\":\"" + phone + "\"}");
        return new PhoneAuthorizeResponse(
            target.studentId(),
            target.studentNo(),
            phone,
            current.studentNo(),
            "ACTIVE",
            true);
    }

    public TradePrecheckResponse tradePrecheck(String authorizationHeader) {
        StudentSession student = requireStudent(authorizationHeader);
        if (student.mobile() == null || student.mobile().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "完成手机号授权后可继续交易");
        }
        return new TradePrecheckResponse(student.studentId(), student.studentNo(), true);
    }

    public StudentMeResponse me(String authorizationHeader) {
        StudentSession student = requireStudent(authorizationHeader);
        return new StudentMeResponse(
            student.studentId(),
            student.studentNo(),
            student.userId(),
            student.mobile(),
            student.nickname(),
            student.status(),
            student.mergedToStudentId());
    }

    public StudentSession requireStudent(String authorizationHeader) {
        String studentNo = parseBearerToken(authorizationHeader);
        StudentSession student = findByStudentNo(studentNo)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "学员登录态无效"));
        if ("MERGED".equals(student.status())) {
            return resolveMerged(student);
        }
        if (!"ACTIVE".equals(student.status()) || !"ACTIVE".equals(student.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "学员身份不存在或已禁用");
        }
        return student;
    }

    private AppWechatLoginResponse toLoginResponse(StudentSession student) {
        return new AppWechatLoginResponse(
            student.userNo(),
            student.studentId(),
            student.studentNo(),
            TOKEN_PREFIX + student.studentNo(),
            student.mobile() != null && !student.mobile().isBlank(),
            student.mobile(),
            7200,
            OffsetDateTime.now().plusSeconds(7200));
    }

    private StudentSession resolveMerged(StudentSession student) {
        if (!"MERGED".equals(student.status())) {
            return student;
        }
        if (student.mergedToStudentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "学员合并状态异常");
        }
        return findById(student.mergedToStudentId())
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "主学员档案不存在"));
    }

    private void bindPhone(StudentSession student, String phone) {
        jdbcTemplate.update(
            """
            update edu_student
            set mobile = ?,
                updated_by = 0,
                version = version + 1
            where id = ?
            """,
            phone,
            student.studentId());
        jdbcTemplate.update(
            """
            update sys_user
            set mobile = ?,
                updated_by = 0,
                version = version + 1
            where id = ?
            """,
            phone,
            student.userId());
    }

    private String parseMockPhone(String phoneCode) {
        String value = phoneCode == null ? "" : phoneCode.trim();
        if (!value.startsWith("mock:")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "S4 仅支持 mock 手机号授权");
        }
        String phone = value.substring("mock:".length()).trim();
        if (!phone.matches("1[3-9]\\d{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "手机号格式错误");
        }
        return phone;
    }

    private String parseBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少学员登录态");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (!token.startsWith(TOKEN_PREFIX) || token.length() == TOKEN_PREFIX.length()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "学员登录态无效");
        }
        return token.substring(TOKEN_PREFIX.length());
    }

    private void updateLastLogin(long userId) {
        jdbcTemplate.update("update sys_user set last_login_at = ? where id = ?", java.time.LocalDateTime.now(), userId);
    }

    private Optional<StudentSession> findByUserNo(String userNo) {
        return queryOne(
            """
            select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.wx_openid, s.wx_unionid,
                   s.status, s.merged_to_student_id, u.user_no, u.status as user_status
            from sys_user u
            join edu_student s on s.user_id = u.id
            where u.user_no = ? and u.user_type = 'STUDENT' and u.deleted_flag = 0 and s.deleted_flag = 0
            """,
            userNo);
    }

    private Optional<StudentSession> findByOpenid(String wxOpenid) {
        return queryOne(
            """
            select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.wx_openid, s.wx_unionid,
                   s.status, s.merged_to_student_id, u.user_no, u.status as user_status
            from edu_student s
            join sys_user u on u.id = s.user_id
            where s.wx_openid = ? and u.user_type = 'STUDENT' and u.deleted_flag = 0 and s.deleted_flag = 0
            """,
            wxOpenid);
    }

    private Optional<StudentSession> findByStudentNo(String studentNo) {
        return queryOne(
            """
            select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.wx_openid, s.wx_unionid,
                   s.status, s.merged_to_student_id,
                   coalesce(u.user_no, '') as user_no,
                   coalesce(u.status, 'ACTIVE') as user_status
            from edu_student s
            left join sys_user u on u.id = s.user_id
            where s.student_no = ?
              and (u.id is null or (u.user_type = 'STUDENT' and u.deleted_flag = 0))
              and s.deleted_flag = 0
            """,
            studentNo);
    }

    private Optional<StudentSession> findById(long studentId) {
        return jdbcTemplate.query(
                """
                select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.wx_openid, s.wx_unionid,
                       s.status, s.merged_to_student_id, u.user_no, u.status as user_status
                from edu_student s
                join sys_user u on u.id = s.user_id
                where s.id = ? and u.user_type = 'STUDENT' and u.deleted_flag = 0 and s.deleted_flag = 0
                """,
                (rs, rowNum) -> mapStudent(rs),
                studentId)
            .stream()
            .findFirst();
    }

    private Optional<StudentSession> findActiveByMobile(String mobile) {
        return jdbcTemplate.query(
                """
                select s.id, s.student_no, s.user_id, s.mobile, s.nickname, s.wx_openid, s.wx_unionid,
                       s.status, s.merged_to_student_id,
                       coalesce(u.user_no, '') as user_no,
                       coalesce(u.status, 'ACTIVE') as user_status
                from edu_student s
                left join sys_user u on u.id = s.user_id
                where s.mobile = ? and s.status = 'ACTIVE' and s.deleted_flag = 0
                order by s.id
                limit 1
                """,
                (rs, rowNum) -> mapStudent(rs),
                mobile)
            .stream()
            .findFirst();
    }

    private Optional<StudentSession> queryOne(String sql, String value) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapStudent(rs), value).stream().findFirst();
    }

    private StudentSession mapStudent(java.sql.ResultSet rs) throws java.sql.SQLException {
        long userId = rs.getLong("user_id");
        Long mergedTo = rs.getLong("merged_to_student_id");
        if (rs.wasNull()) {
            mergedTo = null;
        }
        return new StudentSession(
            rs.getLong("id"),
            rs.getString("student_no"),
            userId,
            rs.getString("user_no"),
            rs.getString("mobile"),
            rs.getString("nickname"),
            rs.getString("wx_openid"),
            rs.getString("wx_unionid"),
            rs.getString("status"),
            mergedTo,
            rs.getString("user_status"));
    }

    public record AppWechatLoginCommand(String wxCode, String sourceChannel) {
    }

    public record AppWechatLoginResponse(
        String userNo,
        long studentId,
        String studentNo,
        String accessToken,
        boolean mobileBound,
        String mobile,
        int expiresIn,
        OffsetDateTime expireAt
    ) {
    }

    public record PhoneAuthorizeCommand(String phoneCode) {
    }

    public record PhoneAuthorizeResponse(
        long studentId,
        String studentNo,
        String mobile,
        String mergedFromStudentNo,
        String status,
        boolean mobileBound
    ) {
    }

    public record TradePrecheckResponse(long studentId, String studentNo, boolean mobileBound) {
    }

    public record StudentMeResponse(
        long studentId,
        String studentNo,
        long userId,
        String mobile,
        String nickname,
        String status,
        Long mergedToStudentId
    ) {
    }

    public record StudentSession(
        long studentId,
        String studentNo,
        long userId,
        String userNo,
        String mobile,
        String nickname,
        String wxOpenid,
        String wxUnionid,
        String status,
        Long mergedToStudentId,
        String userStatus
    ) {
    }
}
