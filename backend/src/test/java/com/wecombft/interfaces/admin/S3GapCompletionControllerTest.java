package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class S3GapCompletionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_support_mock_wecom_login_and_return_edu_admin_and_teacher_permissions() throws Exception {
        String teacherToken = mockWecomLogin("mock:DEMO_TEACHER");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + teacherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].role_code", hasItem("TEACHER")))
            .andExpect(jsonPath("$.data.permission_codes", hasItem("course:lesson:write")))
            .andExpect(jsonPath("$.data.data_scope.scope_code").value("OWN_COURSE"))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("course.lesson-content")))
            .andExpect(jsonPath("$.data.field_masks[*].mask_strategy", hasItem("LEARNING_RECORD_FULL")));

        String eduAdminToken = mockWecomLogin("mock:DEMO_EDU_ADMIN");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + eduAdminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].role_code", hasItem("EDU_ADMIN")))
            .andExpect(jsonPath("$.data.permission_codes", hasItem("course:spec:write")))
            .andExpect(jsonPath("$.data.data_scope.scope_code").value("COURSE_ALL"))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("course.manage")));
    }

    @Test
    void should_issue_app_and_supplier_h5_mock_tokens_for_later_auth_boundaries() throws Exception {
        mockMvc.perform(post("/api/app/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"wx_code":"mock:DEMO_APP_STUDENT","source_channel":"S3_TEST"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_no").value("DEMO_APP_STUDENT"))
            .andExpect(jsonPath("$.data.student_no").value("STU_S3_DEMO"))
            .andExpect(jsonPath("$.data.access_token").value("S3-APP-DEMO-STU_S3_DEMO"))
            .andExpect(jsonPath("$.data.mobile_bound").value(true));

        mockMvc.perform(post("/api/supplier-h5/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"access_token":"mock:SUPPLIER_S3","supplier_no":"SUP_S3_DEMO"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.supplier_no").value("SUP_S3_DEMO"))
            .andExpect(jsonPath("$.data.supplier_name").value("S3 演示供货商"))
            .andExpect(jsonPath("$.data.session_token").value("S3-SUPPLIER-DEMO-SUP_S3_DEMO"));
    }

    @Test
    void should_create_role_application_notifications_and_query_approval_todos() throws Exception {
        String applicantToken = loginAs("DEMO_UNASSIGNED");
        String adminToken = loginAs("DEMO_ADMIN");

        mockMvc.perform(post("/api/admin/role-applications")
                .header("Authorization", "Bearer " + applicantToken)
                .header("Idempotency-Key", "s3-gap-role-application")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role_code":"TEACHER","submit_reason":"need to maintain course lessons"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.role_code").value("TEACHER"));

        mockMvc.perform(get("/api/collab/approvals")
                .header("Authorization", "Bearer " + adminToken)
                .param("role", "APPROVER")
                .param("approval_type", "ROLE_APPLICATION")
                .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].related_object_no", hasItem("TEACHER")));

        mockMvc.perform(get("/api/collab/notifications")
                .header("Authorization", "Bearer " + adminToken)
                .param("scene_code", "ROLE_APPLICATION"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].channel", hasItem("IN_APP")))
            .andExpect(jsonPath("$.data.records[*].channel", hasItem("WECOM_CARD")));

        Integer notificationCount = jdbcTemplate.queryForObject(
                """
                select count(*) from notify_message
                where scene_code = 'ROLE_APPLICATION'
                  and related_object_type = 'ROLE_APPLICATION'
                """,
                Integer.class);
        assertThat(notificationCount).isEqualTo(2);
    }

    @Test
    void should_write_audit_for_permission_denied_and_state_conflict() throws Exception {
        String opsToken = loginAs("DEMO_OPS");

        mockMvc.perform(get("/api/admin/audit/logs")
                .header("Authorization", "Bearer " + opsToken)
                .header("X-Trace-Id", "trace-s3-permission-denied"))
            .andExpect(status().isForbidden());

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-s3-permission-denied'
                  and operation_module = 'SECURITY'
                  and operation_type = 'PERMISSION_DENIED'
                  and result = 'FAILED'
                """,
                Integer.class);
        assertThat(deniedAuditCount).isEqualTo(1);

        String applicantToken = loginAs("DEMO_UNASSIGNED");
        String adminToken = loginAs("DEMO_ADMIN");
        mockMvc.perform(post("/api/admin/role-applications")
                .header("Authorization", "Bearer " + applicantToken)
                .header("Idempotency-Key", "s3-gap-state-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role_code":"SERVICE","submit_reason":"need service role"}
                    """))
            .andExpect(status().isCreated());
        Long approvalId = jdbcTemplate.queryForObject(
                """
                select id from approval_record
                where related_object_no = 'SERVICE'
                  and applicant_user_id = (select id from sys_user where user_no = 'DEMO_UNASSIGNED')
                  and status = 'PENDING'
                """,
                Long.class);

        mockMvc.perform(post("/api/collab/approvals/{approval_id}/actions", approvalId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approval_comment":"first approval"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/collab/approvals/{approval_id}/actions", approvalId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Trace-Id", "trace-s3-state-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approval_comment":"duplicate approval"}
                    """))
            .andExpect(status().isConflict());

        Integer conflictAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-s3-state-conflict'
                  and operation_module = 'SECURITY'
                  and operation_type = 'STATE_CONFLICT'
                  and result = 'FAILED'
                """,
                Integer.class);
        assertThat(conflictAuditCount).isEqualTo(1);
    }

    @Test
    void should_enforce_sample_data_scope_and_mask_sample_fields() throws Exception {
        String teacherToken = loginAs("DEMO_TEACHER");
        mockMvc.perform(get("/api/admin/security/scope-samples/teacher-owned-course")
                .header("Authorization", "Bearer " + teacherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sample_id").value("teacher-owned-course"))
            .andExpect(jsonPath("$.data.student_mobile").value("139****0000"))
            .andExpect(jsonPath("$.data.learning_record").value("VISIBLE"));

        mockMvc.perform(get("/api/admin/security/scope-samples/other-teacher-course")
                .header("Authorization", "Bearer " + teacherToken)
                .header("X-Trace-Id", "trace-s3-scope-denied"))
            .andExpect(status().isForbidden());

        String eduAdminToken = loginAs("DEMO_EDU_ADMIN");
        mockMvc.perform(get("/api/admin/security/scope-samples/other-teacher-course")
                .header("Authorization", "Bearer " + eduAdminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sample_id").value("other-teacher-course"));
    }

    @Test
    void should_include_purchase_file_and_notification_config_groups() throws Exception {
        String adminToken = loginAs("DEMO_ADMIN");

        mockMvc.perform(get("/api/admin/system/configs")
                .header("Authorization", "Bearer " + adminToken)
                .param("config_group", "PURCHASE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.config_items[*].config_key", hasItem("PURCHASE_APPROVAL_THRESHOLD_CENT")));

        mockMvc.perform(get("/api/admin/system/configs")
                .header("Authorization", "Bearer " + adminToken)
                .param("config_group", "FILE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.config_items[*].config_key", hasItem("FILE_MAX_UPLOAD_SIZE_MB")));

        mockMvc.perform(get("/api/admin/system/configs")
                .header("Authorization", "Bearer " + adminToken)
                .param("config_group", "NOTIFICATION"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.config_items[*].config_key", hasItem("WECOM_CARD_MOCK_ENABLED")))
            .andExpect(content().string(not(org.hamcrest.Matchers.containsString("secret"))));
    }

    private String mockWecomLogin(String authCode) throws Exception {
        String response = mockMvc.perform(post("/api/admin/auth/wecom-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"auth_code":"%s","redirect_uri":"http://localhost/admin"}
                    """.formatted(authCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.access_token").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractToken(response);
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
        return extractToken(response);
    }

    private String extractToken(String response) {
        int marker = response.indexOf("\"access_token\":\"");
        int start = marker + "\"access_token\":\"".length();
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
