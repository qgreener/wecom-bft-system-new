package com.wecombft.application.course;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.student.AppStudentApplicationService;
import com.wecombft.application.student.AppStudentApplicationService.StudentSession;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class CourseApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;
    private final AppStudentApplicationService appStudentApplicationService;

    public CourseApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        AuditLogService auditLogService,
        AppStudentApplicationService appStudentApplicationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
        this.appStudentApplicationService = appStudentApplicationService;
    }

    @Transactional
    public CourseSaveResponse saveCourse(CourseSaveCommand command) {
        AdminPrincipal principal = currentPrincipal();
        validateTaxRule(command.defaultTaxRuleId());
        long courseId = idGenerator.nextId();
        String courseNo = "COURSE" + courseId;
        jdbcTemplate.update(
            """
            insert into course (
                id, course_no, course_title, course_type, cover_url, summary, detail,
                teacher_user_id, category_code, course_group_qr, default_tax_rule_id,
                status, sale_start_at, sale_end_at, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)
            """,
            courseId,
            courseNo,
            command.courseTitle(),
            command.courseType(),
            command.coverUrl(),
            command.summary(),
            command.detail(),
            command.teacherUserId(),
            command.categoryCode(),
            command.courseGroupQr(),
            command.defaultTaxRuleId(),
            command.saleStartAt(),
            command.saleEndAt(),
            principal.userId(),
            principal.userId());
        auditLogService.writeSuccess(
            principal,
            "COURSE",
            "COURSE_SAVE",
            "COURSE",
            courseId,
            courseNo,
            null,
            "{\"course_title\":\"" + command.courseTitle() + "\",\"status\":\"DRAFT\"}");
        return new CourseSaveResponse(courseId, courseNo, "DRAFT", null);
    }

    @Transactional
    public CourseSpecResponse saveSpec(long courseId, CourseSpecCommand command) {
        AdminPrincipal principal = currentPrincipal();
        CourseRow course = requireCourse(courseId);
        ensureCourseWritableScope(principal, course);
        validateTaxRule(command.taxRuleId() == null ? course.defaultTaxRuleId() : command.taxRuleId());
        if (command.containsPhysical()) {
            if (command.skuId() == null && command.giftSkuId() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "含实物规格必须绑定 SKU 或赠品 SKU");
            }
            validateSku(command.skuId());
            validateSku(command.giftSkuId());
        }

        long specId = idGenerator.nextId();
        String specNo = "SPEC" + specId;
        jdbcTemplate.update(
            """
            insert into course_spec (
                id, spec_no, course_id, spec_name, sale_price_cent, origin_price_cent,
                stock_mode, contains_physical, sku_id, gift_sku_id, tax_rule_id,
                amount_split_snapshot, status, sort_no, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            specId,
            specNo,
            courseId,
            command.specName(),
            command.salePriceCent(),
            command.originPriceCent(),
            command.stockMode(),
            command.containsPhysical(),
            command.skuId(),
            command.giftSkuId(),
            command.taxRuleId(),
            command.amountSplitSnapshot(),
            command.status() == null ? "ENABLED" : command.status(),
            command.sortNo(),
            principal.userId(),
            principal.userId());
        auditLogService.writeSuccess(
            principal,
            "COURSE",
            "COURSE_SPEC_SAVE",
            "COURSE",
            courseId,
            course.courseNo(),
            null,
            "{\"spec_id\":" + specId + ",\"tax_rule_id\":" + command.taxRuleId() + "}");
        return new CourseSpecResponse(
            specId,
            specNo,
            courseId,
            command.specName(),
            command.salePriceCent(),
            command.status() == null ? "ENABLED" : command.status(),
            command.containsPhysical());
    }

    @Transactional
    public LessonNodeResponse saveLessonNode(long courseId, LessonNodeCommand command) {
        AdminPrincipal principal = currentPrincipal();
        CourseRow course = requireCourse(courseId);
        ensureCourseWritableScope(principal, course);
        if ("LESSON".equals(command.nodeType()) && "LIVE".equals(command.lessonType())
            && (command.liveStartAt() == null || command.liveEndAt() == null)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "直播课节必须填写开始和结束时间");
        }
        long nodeId = idGenerator.nextId();
        jdbcTemplate.update(
            """
            insert into course_lesson_node (
                id, course_id, parent_node_id, node_type, title, lesson_type,
                live_start_at, live_end_at, replay_url, resource_file, status,
                sort_no, remind_enabled, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            nodeId,
            courseId,
            command.parentNodeId(),
            command.nodeType(),
            command.title(),
            command.lessonType(),
            command.liveStartAt(),
            command.liveEndAt(),
            command.replayUrl(),
            command.resourceFile(),
            command.status() == null ? "PUBLISHED" : command.status(),
            command.sortNo(),
            command.remindEnabled(),
            principal.userId(),
            principal.userId());
        auditLogService.writeSuccess(
            principal,
            "COURSE",
            "COURSE_LESSON_SAVE",
            "COURSE",
            courseId,
            course.courseNo(),
            null,
            "{\"node_id\":" + nodeId + ",\"title\":\"" + command.title() + "\"}");
        return new LessonNodeResponse(
            nodeId,
            courseId,
            command.parentNodeId(),
            command.nodeType(),
            command.title(),
            command.lessonType(),
            command.status() == null ? "PUBLISHED" : command.status(),
            command.sortNo());
    }

    @Transactional
    public CourseApprovalResponse submitApproval(long courseId, CourseApprovalCommand command) {
        AdminPrincipal principal = currentPrincipal();
        CourseRow course = requireCourse(courseId);
        ensureCourseWritableScope(principal, course);
        String approvalType = normalizeCourseApprovalType(command.approvalType());
        if ("COURSE_ON_SHELF".equals(approvalType) && countEnabledSpecs(courseId) == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "无启用规格不可上架");
        }
        Integer pending = jdbcTemplate.queryForObject(
            """
            select count(*) from approval_record
            where related_object_type = 'COURSE' and related_object_id = ? and approval_type = ? and status = 'PENDING'
            """,
            Integer.class,
            courseId,
            approvalType);
        if (pending != null && pending > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "课程存在待审批记录");
        }

        LocalDateTime deleteNoticeDeadline = "COURSE_DELETE".equals(approvalType)
            ? command.deleteNoticeDeadline() == null ? LocalDateTime.now().plusDays(3) : command.deleteNoticeDeadline()
            : null;
        long approvalId = idGenerator.nextId();
        String approvalNo = "APR" + approvalId;
        jdbcTemplate.update(
            """
            insert into approval_record (
                id, approval_no, approval_type, title, applicant_user_id, related_object_type,
                related_object_id, related_object_no, related_object_status_before, status, submit_reason, submitted_at,
                created_by, updated_by
            ) values (?, ?, ?, ?, ?, 'COURSE', ?, ?, ?, 'PENDING', ?, ?, ?, ?)
            """,
            approvalId,
            approvalNo,
            approvalType,
            course.courseTitle() + " " + command.approvalType(),
            principal.userId(),
            courseId,
            course.courseNo(),
            course.status(),
            command.submitReason(),
            LocalDateTime.now(),
            principal.userId(),
            principal.userId());
        jdbcTemplate.update(
            """
            update course
            set status = 'PENDING_REVIEW',
                delete_notice_deadline = case when ? = 'COURSE_DELETE' then ? else delete_notice_deadline end,
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            approvalType,
            deleteNoticeDeadline,
            principal.userId(),
            courseId);
        auditLogService.writeSuccess(
            principal,
            "COURSE",
            "COURSE_APPROVAL_SUBMIT",
            "COURSE",
            courseId,
            course.courseNo(),
            null,
            "{\"approval_id\":" + approvalId + ",\"approval_type\":\"" + approvalType + "\"}");
        return new CourseApprovalResponse(
            approvalId,
            approvalNo,
            courseId,
            approvalType,
            "PENDING",
            "PENDING_REVIEW");
    }

    @Transactional
    public CourseApprovalActionResponse approvalAction(long approvalId, CourseApprovalActionCommand command) {
        AdminPrincipal principal = currentPrincipal();
        ApprovalRow approval = findApproval(approvalId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "审批记录不存在"));
        if (!"PENDING".equals(approval.status())) {
            auditLogService.writeFailure(
                principal,
                "COURSE",
                "STATE_CONFLICT",
                "APPROVAL",
                approvalId,
                approval.approvalNo(),
                null,
                "审批已处理");
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "审批已处理");
        }
        String actionStatus = "APPROVE".equals(command.action()) ? "APPROVED" : "REJECTED";
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
            actionStatus,
            principal.userId(),
            command.approvalComment(),
            LocalDateTime.now(),
            principal.userId(),
            approvalId);

        String courseStatus = statusAfterApproval(approval, actionStatus);
        auditLogService.writeSuccess(
            principal,
            "COURSE",
            "COURSE_APPROVAL_" + command.action(),
            "COURSE",
            approval.courseId(),
            approval.courseNo(),
            null,
            "{\"approval_id\":" + approvalId + ",\"course_status\":\"" + courseStatus + "\"}");
        return new CourseApprovalActionResponse(approvalId, approval.courseId(), approval.approvalType(), actionStatus, courseStatus);
    }

    public CourseDetailResponse adminCourseDetail(long courseId) {
        AdminPrincipal principal = currentPrincipal();
        CourseRow course = requireCourse(courseId);
        ensureCourseReadableScope(principal, course);
        return toCourseDetail(course, true);
    }

    public CoursePage adminCourses(String keyword, String status, Integer pageNo, Integer pageSize) {
        AdminPrincipal principal = currentPrincipal();
        StringBuilder sql = new StringBuilder(
            """
            select c.*, coalesce(min(s.sale_price_cent), 0) as min_sale_price_cent
            from course c
            left join course_spec s on s.course_id = c.id and s.deleted_flag = 0
            where c.deleted_flag = 0
            """);
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and c.course_title like ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" and c.status = ?");
            args.add(status);
        }
        if (isTeacherOnly(principal)) {
            sql.append(" and c.teacher_user_id = ?");
            args.add(principal.userId());
        }
        sql.append(" group by c.id order by c.created_at desc limit ? offset ?");
        int size = pageSize == null ? 20 : pageSize;
        int page = pageNo == null ? 1 : pageNo;
        args.add(size);
        args.add((page - 1) * size);
        List<CourseListItem> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapCourseListItem(rs), args.toArray());
        return new CoursePage(records, page, size, records.size());
    }

    public AppCoursePage appCourses(String keyword, String categoryCode, String courseType, Integer pageNo, Integer pageSize) {
        StringBuilder sql = new StringBuilder(
            """
            select c.*, min(s.sale_price_cent) as min_sale_price_cent
            from course c
            join course_spec s on s.course_id = c.id and s.status = 'ENABLED' and s.deleted_flag = 0
            where c.status = 'ON_SHELF'
              and c.deleted_flag = 0
              and (c.sale_start_at is null or c.sale_start_at <= ?)
              and (c.sale_end_at is null or c.sale_end_at >= ?)
            """);
        LocalDateTime now = LocalDateTime.now();
        List<Object> args = new ArrayList<>();
        args.add(now);
        args.add(now);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and c.course_title like ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            sql.append(" and c.category_code = ?");
            args.add(categoryCode);
        }
        if (courseType != null && !courseType.isBlank()) {
            sql.append(" and c.course_type = ?");
            args.add(courseType);
        }
        sql.append(" group by c.id order by c.published_at desc, c.id desc limit ? offset ?");
        int size = pageSize == null ? 20 : pageSize;
        int page = pageNo == null ? 1 : pageNo;
        args.add(size);
        args.add((page - 1) * size);
        List<AppCourseListItem> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new AppCourseListItem(
            rs.getLong("id"),
            rs.getString("course_no"),
            rs.getString("course_title"),
            rs.getString("cover_url"),
            rs.getString("summary"),
            rs.getString("course_type"),
            rs.getObject("sale_start_at", LocalDateTime.class),
            rs.getObject("sale_end_at", LocalDateTime.class),
            rs.getLong("min_sale_price_cent"),
            rs.getString("status")), args.toArray());
        return new AppCoursePage(records, page, size, records.size());
    }

    public CourseDetailResponse appCourseDetail(long courseId) {
        CourseRow course = requireCourse(courseId);
        if (!"ON_SHELF".equals(course.status())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "课程不存在或未上架");
        }
        return toCourseDetail(course, false);
    }

    public CourseSpecsResponse appCourseSpecs(long courseId) {
        CourseRow course = requireCourse(courseId);
        if (!"ON_SHELF".equals(course.status())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "课程不存在或未上架");
        }
        return new CourseSpecsResponse(courseId, findSpecs(courseId, true));
    }

    public EntitlementPage appEntitlements(String authorizationHeader, String status, Integer pageNo, Integer pageSize) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        StringBuilder sql = new StringBuilder(
            """
            select id, entitlement_no, order_id, order_no, course_id, spec_id, status,
                   opened_at, expire_at, remind_stopped, course_snapshot
            from learning_entitlement
            where student_id = ?
            """);
        List<Object> args = new ArrayList<>();
        args.add(student.studentId());
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        sql.append(" order by opened_at desc, id desc limit ? offset ?");
        int size = pageSize == null ? 20 : pageSize;
        int page = pageNo == null ? 1 : pageNo;
        args.add(size);
        args.add((page - 1) * size);
        List<EntitlementItem> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new EntitlementItem(
            rs.getLong("id"),
            rs.getString("entitlement_no"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getLong("course_id"),
            rs.getLong("spec_id"),
            rs.getString("status"),
            rs.getObject("opened_at", LocalDateTime.class),
            rs.getObject("expire_at", LocalDateTime.class),
            rs.getInt("remind_stopped") == 1,
            rs.getString("course_snapshot")), args.toArray());
        return new EntitlementPage(records, page, size, records.size());
    }

    public EntitlementLessonsResponse appEntitlementLessons(String authorizationHeader, long entitlementId) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        EntitlementRow entitlement = findEntitlement(entitlementId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "学习权益不存在"));
        if (entitlement.studentId() != student.studentId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该权益");
        }
        if (!"ACTIVE".equals(entitlement.status())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "权益冻结或撤销后不可学习");
        }
        CourseRow course = requireCourse(entitlement.courseId());
        return new EntitlementLessonsResponse(
            entitlement.id(),
            course.id(),
            course.courseGroupQr(),
            findLessonNodes(course.id(), true));
    }

    private CourseDetailResponse toCourseDetail(CourseRow course, boolean includeHiddenLessons) {
        return new CourseDetailResponse(
            course.id(),
            course.courseNo(),
            course.courseTitle(),
            course.courseType(),
            course.coverUrl(),
            course.summary(),
            course.detail(),
            course.teacherUserId(),
            course.categoryCode(),
            course.courseGroupQr(),
            course.defaultTaxRuleId(),
            course.status(),
            findSpecs(course.id(), false),
            findLessonNodes(course.id(), !includeHiddenLessons));
    }

    private List<CourseSpecItem> findSpecs(long courseId, boolean enabledOnly) {
        String sql = """
            select id, spec_no, course_id, spec_name, sale_price_cent, origin_price_cent,
                   stock_mode, contains_physical, sku_id, gift_sku_id, tax_rule_id,
                   amount_split_snapshot, status, sort_no
            from course_spec
            where course_id = ? and deleted_flag = 0
            """ + (enabledOnly ? " and status = 'ENABLED'" : "") + " order by sort_no, id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new CourseSpecItem(
            rs.getLong("id"),
            rs.getString("spec_no"),
            rs.getLong("course_id"),
            rs.getString("spec_name"),
            rs.getLong("sale_price_cent"),
            nullableLong(rs, "origin_price_cent"),
            rs.getString("stock_mode"),
            rs.getInt("contains_physical") == 1,
            nullableLong(rs, "sku_id"),
            nullableLong(rs, "gift_sku_id"),
            nullableLong(rs, "tax_rule_id"),
            rs.getString("amount_split_snapshot"),
            rs.getString("status"),
            rs.getInt("sort_no")), courseId);
    }

    private List<LessonNodeItem> findLessonNodes(long courseId, boolean publishedOnly) {
        String sql = """
            select id, course_id, parent_node_id, node_type, title, lesson_type,
                   live_start_at, live_end_at, replay_url, resource_file, status, sort_no
            from course_lesson_node
            where course_id = ? and deleted_flag = 0
            """ + (publishedOnly ? " and status = 'PUBLISHED'" : "") + " order by sort_no, id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new LessonNodeItem(
            rs.getLong("id"),
            rs.getLong("course_id"),
            nullableLong(rs, "parent_node_id"),
            rs.getString("node_type"),
            rs.getString("title"),
            rs.getString("lesson_type"),
            rs.getObject("live_start_at", LocalDateTime.class),
            rs.getObject("live_end_at", LocalDateTime.class),
            rs.getString("replay_url"),
            rs.getString("resource_file"),
            rs.getString("status"),
            rs.getInt("sort_no")), courseId);
    }

    private CourseRow requireCourse(long courseId) {
        return jdbcTemplate.query(
                """
                select id, course_no, course_title, course_type, cover_url, summary, detail,
                       teacher_user_id, category_code, course_group_qr, default_tax_rule_id,
                       status, sale_start_at, sale_end_at
                from course
                where id = ? and deleted_flag = 0
                """,
                (rs, rowNum) -> mapCourse(rs),
                courseId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "课程不存在"));
    }

    private Optional<ApprovalRow> findApproval(long approvalId) {
        return jdbcTemplate.query(
                """
                select id, approval_no, approval_type, related_object_id, related_object_no,
                       related_object_status_before, status
                from approval_record
                where id = ? and related_object_type = 'COURSE'
                """,
                (rs, rowNum) -> new ApprovalRow(
                    rs.getLong("id"),
                    rs.getString("approval_no"),
                    rs.getString("approval_type"),
                    rs.getLong("related_object_id"),
                    rs.getString("related_object_no"),
                    rs.getString("related_object_status_before"),
                    rs.getString("status")),
                approvalId)
            .stream()
            .findFirst();
    }

    private Optional<EntitlementRow> findEntitlement(long entitlementId) {
        return jdbcTemplate.query(
                """
                select id, student_id, course_id, status
                from learning_entitlement
                where id = ?
                """,
                (rs, rowNum) -> new EntitlementRow(
                    rs.getLong("id"),
                    rs.getLong("student_id"),
                    rs.getLong("course_id"),
                    rs.getString("status")),
                entitlementId)
            .stream()
            .findFirst();
    }

    private String statusAfterApproval(ApprovalRow approval, String actionStatus) {
        String courseStatus;
        if ("APPROVED".equals(actionStatus)) {
            courseStatus = switch (approval.approvalType()) {
                case "COURSE_ON_SHELF" -> "ON_SHELF";
                case "COURSE_OFF_SHELF" -> "OFF_SHELF";
                case "COURSE_DELETE" -> "DELETE_PENDING";
                default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "审批类型不支持");
            };
            jdbcTemplate.update(
                """
                update course
                set status = ?,
                    published_at = case when ? = 'ON_SHELF' then ? else published_at end,
                    updated_by = 0,
                    version = version + 1
                where id = ?
                """,
                courseStatus,
                courseStatus,
                LocalDateTime.now(),
                approval.courseId());
        } else {
            String restoreStatus = approval.statusBefore() == null ? "DRAFT" : approval.statusBefore();
            jdbcTemplate.update(
                """
                update course
                set status = ?,
                    delete_notice_deadline = case when ? = 'COURSE_DELETE' then null else delete_notice_deadline end,
                    updated_by = 0,
                    version = version + 1
                where id = ? and status = 'PENDING_REVIEW'
                """,
                restoreStatus,
                approval.approvalType(),
                approval.courseId());
            courseStatus = restoreStatus;
        }
        return courseStatus;
    }

    private String normalizeCourseApprovalType(String type) {
        return switch (type) {
            case "ON_SHELF", "COURSE_ON_SHELF" -> "COURSE_ON_SHELF";
            case "OFF_SHELF", "COURSE_OFF_SHELF" -> "COURSE_OFF_SHELF";
            case "DELETE", "COURSE_DELETE" -> "COURSE_DELETE";
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "审批类型不支持");
        };
    }

    private void validateTaxRule(Long taxRuleId) {
        if (taxRuleId == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from tax_rule where id = ? and status = 'ACTIVE' and deleted_flag = 0",
            Integer.class,
            taxRuleId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "税务规则不存在或已停用");
        }
    }

    private void validateSku(Long skuId) {
        if (skuId == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from inventory_sku where id = ? and status = 'ACTIVE' and deleted_flag = 0",
            Integer.class,
            skuId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "SKU 不存在或已停用");
        }
    }

    private int countEnabledSpecs(long courseId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from course_spec where course_id = ? and status = 'ENABLED' and deleted_flag = 0",
            Integer.class,
            courseId);
        return count == null ? 0 : count;
    }

    private void ensureCourseReadableScope(AdminPrincipal principal, CourseRow course) {
        if (!isTeacherOnly(principal)) {
            return;
        }
        if (course.teacherUserId() == null || course.teacherUserId() != principal.userId()) {
            auditLogService.writeFailure(
                principal,
                "COURSE",
                "DATA_SCOPE_DENIED",
                "COURSE",
                course.id(),
                course.courseNo(),
                null,
                "讲师只能访问负责课程");
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问非负责课程");
        }
    }

    private void ensureCourseWritableScope(AdminPrincipal principal, CourseRow course) {
        if (hasScope(principal, "ALL") || hasScope(principal, "COURSE_ALL")) {
            return;
        }
        ensureCourseReadableScope(principal, course);
    }

    private boolean isTeacherOnly(AdminPrincipal principal) {
        return principal.roleCodes().contains("TEACHER")
            && !hasScope(principal, "ALL")
            && !hasScope(principal, "COURSE_ALL");
    }

    private boolean hasScope(AdminPrincipal principal, String scope) {
        return scope.equals(principal.permissionView().dataScope().scopeCode());
    }

    private AdminPrincipal currentPrincipal() {
        AdminPrincipal principal = AdminPrincipalContext.currentOrNull();
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少登录态");
        }
        return principal;
    }

    private CourseRow mapCourse(ResultSet rs) throws SQLException {
        return new CourseRow(
            rs.getLong("id"),
            rs.getString("course_no"),
            rs.getString("course_title"),
            rs.getString("course_type"),
            rs.getString("cover_url"),
            rs.getString("summary"),
            rs.getString("detail"),
            nullableLong(rs, "teacher_user_id"),
            rs.getString("category_code"),
            rs.getString("course_group_qr"),
            nullableLong(rs, "default_tax_rule_id"),
            rs.getString("status"),
            rs.getObject("sale_start_at", LocalDateTime.class),
            rs.getObject("sale_end_at", LocalDateTime.class));
    }

    private CourseListItem mapCourseListItem(ResultSet rs) throws SQLException {
        return new CourseListItem(
            rs.getLong("id"),
            rs.getString("course_no"),
            rs.getString("course_title"),
            rs.getString("course_type"),
            nullableLong(rs, "teacher_user_id"),
            rs.getString("category_code"),
            nullableLong(rs, "default_tax_rule_id"),
            rs.getString("status"),
            rs.getObject("published_at", LocalDateTime.class),
            nullableLong(rs, "min_sale_price_cent"));
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private record CourseRow(
        long id,
        String courseNo,
        String courseTitle,
        String courseType,
        String coverUrl,
        String summary,
        String detail,
        Long teacherUserId,
        String categoryCode,
        String courseGroupQr,
        Long defaultTaxRuleId,
        String status,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt
    ) {
    }

    private record ApprovalRow(
        long id,
        String approvalNo,
        String approvalType,
        long courseId,
        String courseNo,
        String statusBefore,
        String status
    ) {
    }

    private record EntitlementRow(long id, long studentId, long courseId, String status) {
    }

    public record CourseSaveCommand(
        String courseTitle,
        String courseType,
        String coverUrl,
        String summary,
        String detail,
        Long teacherUserId,
        String categoryCode,
        String courseGroupQr,
        Long defaultTaxRuleId,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt
    ) {
    }

    public record CourseSaveResponse(long courseId, String courseNo, String status, LocalDateTime updatedAt) {
    }

    public record CourseSpecCommand(
        String specName,
        long salePriceCent,
        Long originPriceCent,
        String stockMode,
        boolean containsPhysical,
        Long skuId,
        Long giftSkuId,
        Long taxRuleId,
        String amountSplitSnapshot,
        String status,
        int sortNo
    ) {
    }

    public record CourseSpecResponse(
        long specId,
        String specNo,
        long courseId,
        String specName,
        long salePriceCent,
        String status,
        boolean containsPhysical
    ) {
    }

    public record LessonNodeCommand(
        Long parentNodeId,
        String nodeType,
        String title,
        String lessonType,
        LocalDateTime liveStartAt,
        LocalDateTime liveEndAt,
        String replayUrl,
        String resourceFile,
        String status,
        int sortNo,
        boolean remindEnabled
    ) {
    }

    public record LessonNodeResponse(
        long nodeId,
        long courseId,
        Long parentNodeId,
        String nodeType,
        String title,
        String lessonType,
        String status,
        int sortNo
    ) {
    }

    public record CourseApprovalCommand(String approvalType, String submitReason, LocalDateTime deleteNoticeDeadline) {
    }

    public record CourseApprovalResponse(
        long approvalId,
        String approvalNo,
        long courseId,
        String approvalType,
        String status,
        String courseStatus
    ) {
    }

    public record CourseApprovalActionCommand(String action, String approvalComment) {
    }

    public record CourseApprovalActionResponse(
        long approvalId,
        long courseId,
        String approvalType,
        String status,
        String courseStatus
    ) {
    }

    public record CoursePage(List<CourseListItem> records, int pageNo, int pageSize, int total) {
    }

    public record CourseListItem(
        long courseId,
        String courseNo,
        String courseTitle,
        String courseType,
        Long teacherUserId,
        String categoryCode,
        Long defaultTaxRuleId,
        String status,
        LocalDateTime publishedAt,
        Long minSalePriceCent
    ) {
    }

    public record AppCoursePage(List<AppCourseListItem> records, int pageNo, int pageSize, int total) {
    }

    public record AppCourseListItem(
        long courseId,
        String courseNo,
        String courseTitle,
        String coverUrl,
        String summary,
        String courseType,
        LocalDateTime saleStartAt,
        LocalDateTime saleEndAt,
        long minSalePriceCent,
        String status
    ) {
    }

    public record CourseDetailResponse(
        long courseId,
        String courseNo,
        String courseTitle,
        String courseType,
        String coverUrl,
        String summary,
        String detail,
        Long teacherUserId,
        String categoryCode,
        String courseGroupQr,
        Long defaultTaxRuleId,
        String status,
        List<CourseSpecItem> specs,
        List<LessonNodeItem> lessonSummary
    ) {
    }

    public record CourseSpecsResponse(long courseId, List<CourseSpecItem> specs) {
    }

    public record CourseSpecItem(
        long specId,
        String specNo,
        long courseId,
        String specName,
        long salePriceCent,
        Long originPriceCent,
        String stockMode,
        boolean containsPhysical,
        Long skuId,
        Long giftSkuId,
        Long taxRuleId,
        String amountSplitSnapshot,
        String status,
        int sortNo
    ) {
    }

    public record LessonNodeItem(
        long nodeId,
        long courseId,
        Long parentNodeId,
        String nodeType,
        String title,
        String lessonType,
        LocalDateTime liveStartAt,
        LocalDateTime liveEndAt,
        String replayUrl,
        String resourceFile,
        String status,
        int sortNo
    ) {
    }

    public record EntitlementPage(List<EntitlementItem> records, int pageNo, int pageSize, int total) {
    }

    public record EntitlementItem(
        long entitlementId,
        String entitlementNo,
        long orderId,
        String orderNo,
        long courseId,
        long specId,
        String status,
        LocalDateTime openedAt,
        LocalDateTime expireAt,
        boolean remindStopped,
        String courseSnapshot
    ) {
    }

    public record EntitlementLessonsResponse(
        long entitlementId,
        long courseId,
        String courseGroupQr,
        List<LessonNodeItem> nodes
    ) {
    }
}
