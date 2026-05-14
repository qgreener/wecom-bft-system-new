package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class S4CourseLearningControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_create_course_spec_lesson_approve_on_shelf_and_expose_to_app() throws Exception {
        String eduToken = loginAs("DEMO_EDU_ADMIN");
        String adminToken = loginAs("DEMO_ADMIN");

        long courseId = createCourse(eduToken, "S4 可售训练营", 100000000008L);
        long specId = createSpec(eduToken, courseId);
        createLessonNode(eduToken, courseId, "S4 第一课");

        String approvalResponse = mockMvc.perform(post("/api/admin/courses/{course_id}/approval", courseId)
                .header("Authorization", "Bearer " + eduToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approval_type":"ON_SHELF","submit_reason":"S4 上架验收"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long approvalId = extractLong(approvalResponse, "approval_id");

        mockMvc.perform(post("/api/collab/course-approvals/{approval_id}/actions", approvalId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approval_comment":"同意 S4 上架"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.course_id").value(courseId))
            .andExpect(jsonPath("$.data.course_status").value("ON_SHELF"));

        mockMvc.perform(get("/api/app/courses")
                .param("keyword", "S4 可售"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].course_title", hasItem("S4 可售训练营")))
            .andExpect(jsonPath("$.data.records[*].min_sale_price_cent", hasItem(129900)));

        mockMvc.perform(get("/api/app/courses/{course_id}", courseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.course_id").value(courseId))
            .andExpect(jsonPath("$.data.specs[*].spec_name", hasItem("直播训练营")))
            .andExpect(jsonPath("$.data.lesson_summary[*].title", hasItem("S4 第一课")));

        mockMvc.perform(get("/api/app/courses/{course_id}/specs", courseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.specs[*].spec_name", hasItem("直播训练营")));

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where target_type = 'COURSE'
                  and target_id = ?
                  and operation_type in ('COURSE_SAVE', 'COURSE_SPEC_SAVE', 'COURSE_APPROVAL_APPROVE')
                  and result = 'SUCCESS'
                """,
                Integer.class,
                courseId);
        assertThat(auditCount).isGreaterThanOrEqualTo(3);
    }

    @Test
    void should_restore_on_shelf_when_off_shelf_approval_rejected_and_hide_after_delete_approval() throws Exception {
        String eduToken = loginAs("DEMO_EDU_ADMIN");
        String adminToken = loginAs("DEMO_ADMIN");

        long courseId = createCourse(eduToken, "S4 下架删除审批课程", 100000000008L);
        createSpec(eduToken, courseId);
        approveCourse(eduToken, adminToken, courseId, "ON_SHELF", null);

        long offShelfApprovalId = submitApproval(eduToken, courseId, "OFF_SHELF", null);
        mockMvc.perform(post("/api/collab/course-approvals/{approval_id}/actions", offShelfApprovalId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"REJECT","approval_comment":"暂不下架"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.course_status").value("ON_SHELF"));

        mockMvc.perform(get("/api/app/courses/{course_id}", courseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ON_SHELF"));

        long deleteApprovalId = submitApproval(
            eduToken,
            courseId,
            "DELETE",
            "2026-06-05T00:00:00");
        mockMvc.perform(post("/api/collab/course-approvals/{approval_id}/actions", deleteApprovalId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approval_comment":"同意删除"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.course_status").value("DELETE_PENDING"));

        mockMvc.perform(get("/api/app/courses/{course_id}", courseId))
            .andExpect(status().isNotFound());

        String deleteDeadline = jdbcTemplate.queryForObject(
            "select formatdatetime(delete_notice_deadline, 'yyyy-MM-dd''T''HH:mm:ss') from course where id = ?",
            String.class,
            courseId);
        assertThat(deleteDeadline).isEqualTo("2026-06-05T00:00:00");
    }

    @Test
    void should_forbid_teacher_from_accessing_course_outside_own_scope_and_write_audit() throws Exception {
        String eduToken = loginAs("DEMO_EDU_ADMIN");
        long courseId = createCourse(eduToken, "S4 非讲师负责课程", 100000000007L);
        String teacherToken = loginAs("DEMO_TEACHER");

        mockMvc.perform(get("/api/admin/courses/{course_id}", courseId)
                .header("Authorization", "Bearer " + teacherToken)
                .header("X-Trace-Id", "trace-s4-teacher-course-denied"))
            .andExpect(status().isForbidden());

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-s4-teacher-course-denied'
                  and operation_module = 'COURSE'
                  and operation_type = 'DATA_SCOPE_DENIED'
                  and target_id = ?
                  and result = 'FAILED'
                """,
                Integer.class,
                courseId);
        assertThat(deniedAuditCount).isEqualTo(1);
    }

    @Test
    void should_filter_admin_courses_by_documented_teacher_query_param() throws Exception {
        String eduToken = loginAs("DEMO_EDU_ADMIN");
        createCourse(eduToken, "S4 教师筛选目标课程", 100000000007L);
        createCourse(eduToken, "S4 教师筛选干扰课程", 100000000008L);

        mockMvc.perform(get("/api/admin/courses")
                .header("Authorization", "Bearer " + eduToken)
                .param("teacher_user_id", "100000000007"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].course_title", hasItem("S4 教师筛选目标课程")))
            .andExpect(jsonPath("$.data.records[*].course_title", not(hasItem("S4 教师筛选干扰课程"))));
    }

    @Test
    void should_return_learning_center_and_lessons_only_for_active_entitlement() throws Exception {
        String eduToken = loginAs("DEMO_EDU_ADMIN");
        long courseId = createCourse(eduToken, "S4 学习中心课程", 100000000008L);
        long specId = createSpec(eduToken, courseId);
        long lessonId = createLessonNode(eduToken, courseId, "S4 学习页课节");
        long entitlementId = 1000000450L;
        jdbcTemplate.update(
                """
                insert into learning_entitlement (
                    id, entitlement_no, student_id, user_id, order_id, order_no, order_item_id,
                    course_id, spec_id, status, opened_at, remind_stopped, course_snapshot,
                    created_by, updated_by
                ) values (?, ?, 100000000302, 100000000009, ?, ?, ?, ?, ?, 'ACTIVE', ?, 0, ?, 0, 0)
                """,
                entitlementId,
                "ENT_S4_APP",
                entitlementId + 1,
                "ORD_S4_APP",
                entitlementId + 2,
                courseId,
                specId,
                LocalDateTime.now(),
                "{\"course_title\":\"S4 学习中心课程\"}");

        String appToken = appLogin("mock:DEMO_APP_STUDENT");
        mockMvc.perform(get("/api/app/learning/entitlements")
                .header("Authorization", "Bearer " + appToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].entitlement_no", hasItem("ENT_S4_APP")));

        mockMvc.perform(get("/api/app/learning/entitlements/{entitlement_id}/lessons", entitlementId)
                .header("Authorization", "Bearer " + appToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nodes[*].title", hasItem("S4 学习页课节")));

        jdbcTemplate.update("update learning_entitlement set status = 'FROZEN' where id = ?", entitlementId);

        mockMvc.perform(get("/api/app/learning/entitlements/{entitlement_id}/lessons", entitlementId)
                .header("Authorization", "Bearer " + appToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));
    }

    private long createCourse(String token, String title, long teacherUserId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/courses")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "s4-course-" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_title": "%s",
                      "course_type": "LIVE",
                      "cover_url": "https://example.invalid/s4-cover.png",
                      "summary": "S4 summary",
                      "detail": "S4 detail",
                      "teacher_user_id": %d,
                      "category_code": "TRAINING",
                      "course_group_qr": "qr://s4",
                      "default_tax_rule_id": 100000000401,
                      "sale_start_at": "2026-05-01T00:00:00",
                      "sale_end_at": "2026-12-31T23:59:59"
                    }
                    """.formatted(title, teacherUserId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "course_id");
    }

    private long createSpec(String token, long courseId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/courses/{course_id}/specs", courseId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "s4-spec-" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "spec_name": "直播训练营",
                      "sale_price_cent": 129900,
                      "origin_price_cent": 159900,
                      "stock_mode": "VIRTUAL",
                      "contains_physical": true,
                      "sku_id": 100000000402,
                      "gift_sku_id": 100000000403,
                      "tax_rule_id": 100000000401,
                      "amount_split_snapshot": "{\\"training\\":129900}",
                      "status": "ENABLED",
                      "sort_no": 1
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("ENABLED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "spec_id");
    }

    private long createLessonNode(String token, long courseId, String title) throws Exception {
        String response = mockMvc.perform(post("/api/admin/courses/{course_id}/lesson-nodes", courseId)
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "s4-lesson-" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "node_type": "LESSON",
                      "title": "%s",
                      "lesson_type": "LIVE",
                      "live_start_at": "2026-06-01T20:00:00",
                      "live_end_at": "2026-06-01T21:30:00",
                      "status": "PUBLISHED",
                      "sort_no": 1,
                      "remind_enabled": true
                    }
                    """.formatted(title)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "node_id");
    }

    private long submitApproval(String token, long courseId, String approvalType, String deleteDeadline) throws Exception {
        String deadlineJson = deleteDeadline == null ? "" : ",\"delete_notice_deadline\":\"" + deleteDeadline + "\"";
        String response = mockMvc.perform(post("/api/admin/courses/{course_id}/approval", courseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approval_type":"%s","submit_reason":"S4 审批验收"%s}
                    """.formatted(approvalType, deadlineJson)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "approval_id");
    }

    private void approveCourse(String submitToken, String approveToken, long courseId, String approvalType, String deleteDeadline)
        throws Exception {
        long approvalId = submitApproval(submitToken, courseId, approvalType, deleteDeadline);
        mockMvc.perform(post("/api/collab/course-approvals/{approval_id}/actions", approvalId)
                .header("Authorization", "Bearer " + approveToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approval_comment":"同意"}
                    """))
            .andExpect(status().isOk());
    }

    private String loginAs(String userNo) throws Exception {
        String response = mockMvc.perform(post("/api/admin/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"user_no":"%s"}
                    """.formatted(userNo)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "access_token");
    }

    private String appLogin(String wxCode) throws Exception {
        String response = mockMvc.perform(post("/api/app/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"wx_code":"%s","source_channel":"S4_TEST"}
                    """.formatted(wxCode)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "access_token");
    }

    private long extractLong(String response, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int start = response.indexOf(marker) + marker.length();
        int end = start;
        while (end < response.length() && Character.isDigit(response.charAt(end))) {
            end++;
        }
        return Long.parseLong(response.substring(start, end));
    }

    private String extractString(String response, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
