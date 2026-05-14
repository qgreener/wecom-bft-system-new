package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class RoleApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_create_role_application_for_unassigned_user_and_write_audit_log() throws Exception {
        String token = loginAs("DEMO_UNASSIGNED");

        mockMvc.perform(post("/api/admin/role-applications")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "role-application-test-key")
                .header("X-Trace-Id", "trace-role-application")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role_code":"SERVICE","submit_reason":"need to handle refund tickets"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("CREATED"))
            .andExpect(jsonPath("$.data.approval_type").value("ROLE_APPLICATION"))
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        Integer approvalCount = jdbcTemplate.queryForObject(
                """
                select count(*) from approval_record
                where applicant_user_id = (select id from sys_user where user_no = 'DEMO_UNASSIGNED')
                  and related_object_no = 'SERVICE'
                  and status = 'PENDING'
                """,
                Integer.class);
        Integer auditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-role-application'
                  and operation_module = 'IAM'
                  and operation_type = 'ROLE_APPLICATION_SUBMIT'
                  and target_type = 'ROLE_APPLICATION'
                """,
                Integer.class);

        assertThat(approvalCount).isEqualTo(1);
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void should_approve_role_application_and_grant_role_with_audit_log() throws Exception {
        String applicantToken = loginAs("DEMO_UNASSIGNED");
        String adminToken = loginAs("DEMO_ADMIN");

        mockMvc.perform(post("/api/admin/role-applications")
                .header("Authorization", "Bearer " + applicantToken)
                .header("Idempotency-Key", "role-application-approve-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"role_code":"WAREHOUSE","submit_reason":"need to ship paid physical orders"}
                    """))
            .andExpect(status().isCreated());

        Long approvalId = jdbcTemplate.queryForObject(
                """
                select id from approval_record
                where applicant_user_id = (select id from sys_user where user_no = 'DEMO_UNASSIGNED')
                  and related_object_no = 'WAREHOUSE'
                  and status = 'PENDING'
                """,
                Long.class);

        mockMvc.perform(post("/api/collab/approvals/{approval_id}/actions", approvalId)
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Trace-Id", "trace-role-approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approval_comment":"approved for S3 verification"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + applicantToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].role_code", hasItem("WAREHOUSE")))
            .andExpect(jsonPath("$.data.permission_codes", hasItem("fulfillment:shipment:write")));

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-role-approve'
                  and operation_module = 'IAM'
                  and operation_type = 'ROLE_APPLICATION_APPROVE'
                  and target_type = 'ROLE_APPLICATION'
                """,
                Integer.class);

        assertThat(auditCount).isEqualTo(1);
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

        int marker = response.indexOf("\"access_token\":\"");
        int start = marker + "\"access_token\":\"".length();
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
