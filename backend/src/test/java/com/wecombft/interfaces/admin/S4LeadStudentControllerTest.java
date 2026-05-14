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

import com.wecombft.application.crm.LeadApplicationService;
import com.wecombft.application.command.crm.LeadPaidConversionCommand;
import com.wecombft.interfaces.dto.crm.LeadPaidConversionResponse;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class S4LeadStudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LeadApplicationService leadApplicationService;

    @Test
    void should_create_lead_from_public_pc_and_wecom_then_follow_abandon_and_recontact() throws Exception {
        mockMvc.perform(post("/api/h5/lead/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"公开留资","mobile":"13900002001","source_code":"S4_PUBLIC","intent_course_id":null}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING_FOLLOW"));

        String opsToken = loginAs("DEMO_OPS");
        long leadId = createAdminLead(opsToken, "S4 PC 线索", "13900002002", 100000000002L);

        mockMvc.perform(post("/api/admin/leads/{lead_id}/follow-records", leadId)
                .header("Authorization", "Bearer " + opsToken)
                .header("Idempotency-Key", "s4-follow-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"follow_method":"PHONE","content":"首次电话沟通","next_follow_at":"2026-06-01T10:00:00"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("CONTACTED"));

        mockMvc.perform(post("/api/admin/leads/{lead_id}/status", leadId)
                .header("Authorization", "Bearer " + opsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"target_status":"ABANDONED","abandon_reason":"价格不合适"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ABANDONED"));

        mockMvc.perform(post("/api/admin/leads/{lead_id}/status", leadId)
                .header("Authorization", "Bearer " + opsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"target_status":"CONTACTED","next_follow_at":"2026-06-02T10:00:00"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONTACTED"));

        mockMvc.perform(post("/api/wecom/sidebar/leads")
                .header("Authorization", "Bearer " + opsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "wecom_external_user_id":"external_s4_wecom_001",
                      "name":"企微线索",
                      "mobile":"13900002003",
                      "source_channel":"WECOM_SIDEBAR",
                      "source_code":"chat",
                      "owner_user_id":100000000002
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.source_channel").value("WECOM_SIDEBAR"));

        mockMvc.perform(get("/api/admin/leads")
                .header("Authorization", "Bearer " + opsToken)
                .param("keyword", "S4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].name", hasItem("S4 PC 线索")))
            .andExpect(content().string(not(org.hamcrest.Matchers.containsString("13900002001"))));

        Integer followCount = jdbcTemplate.queryForObject(
                "select count(*) from lead_follow_record where lead_id = ?",
                Integer.class,
                leadId);
        assertThat(followCount).isEqualTo(1);
    }

    @Test
    void should_forbid_ops_from_following_unowned_lead_and_write_audit() throws Exception {
        String adminToken = loginAs("DEMO_ADMIN");
        long leadId = createAdminLead(adminToken, "S4 越权线索", "13900002004", 100000000001L);
        String opsToken = loginAs("DEMO_OPS");

        mockMvc.perform(post("/api/admin/leads/{lead_id}/follow-records", leadId)
                .header("Authorization", "Bearer " + opsToken)
                .header("Idempotency-Key", "s4-follow-denied")
                .header("X-Trace-Id", "trace-s4-lead-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"follow_method":"PHONE","content":"越权跟进","next_follow_at":"2026-06-01T10:00:00"}
                    """))
            .andExpect(status().isForbidden());

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-s4-lead-denied'
                  and operation_module = 'CRM'
                  and operation_type = 'DATA_SCOPE_DENIED'
                  and target_id = ?
                  and result = 'FAILED'
                """,
                Integer.class,
                leadId);
        assertThat(deniedAuditCount).isEqualTo(1);
    }

    @Test
    void should_forbid_ops_from_creating_lead_for_other_owner_and_write_audit() throws Exception {
        String opsToken = loginAs("DEMO_OPS");

        mockMvc.perform(post("/api/admin/leads")
                .header("Authorization", "Bearer " + opsToken)
                .header("Idempotency-Key", "s4-lead-create-denied")
                .header("X-Trace-Id", "trace-s4-lead-create-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "S4 创建越权线索",
                      "mobile": "13900002005",
                      "source_channel": "PC_ADMIN",
                      "source_code": "S4",
                      "owner_user_id": 100000000001,
                      "next_follow_at": "2026-06-01T10:00:00",
                      "remark": "S4 denied lead"
                    }
                    """))
            .andExpect(status().isForbidden());

        Integer deniedAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-s4-lead-create-denied'
                  and operation_module = 'CRM'
                  and operation_type = 'DATA_SCOPE_DENIED'
                  and target_type = 'CRM_LEAD'
                  and result = 'FAILED'
                """,
                Integer.class);
        assertThat(deniedAuditCount).isEqualTo(1);
    }

    @Test
    void should_filter_admin_leads_by_documented_query_params() throws Exception {
        String opsToken = loginAs("DEMO_OPS");
        createAdminLead(opsToken, "S4 参数筛选目标线索", "13900002110", 100000000002L);
        createAdminLead(opsToken, "S4 参数筛选干扰线索", "13900002111", 100000000002L);

        mockMvc.perform(get("/api/admin/leads")
                .header("Authorization", "Bearer " + opsToken)
                .param("mobile", "13900002110")
                .param("source_channel", "PC_ADMIN")
                .param("owner_user_id", "100000000002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].name", hasItem("S4 参数筛选目标线索")))
            .andExpect(content().string(not(org.hamcrest.Matchers.containsString("S4 参数筛选干扰线索"))));
    }

    @Test
    void should_login_authorize_phone_merge_student_and_block_unbound_or_conflict_cases() throws Exception {
        String unboundToken = appLogin("mock:wx_s4_unbound");

        mockMvc.perform(post("/api/app/trade/precheck")
                .header("Authorization", "Bearer " + unboundToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));

        mockMvc.perform(post("/api/app/auth/phone-authorize")
                .header("Authorization", "Bearer " + unboundToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"phone_code":"mock:13900000011"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.student_no").value("STU_S4_MANUAL"))
            .andExpect(jsonPath("$.data.mobile").value("13900000011"))
            .andExpect(jsonPath("$.data.mobile_bound").value(true))
            .andExpect(jsonPath("$.data.merged_from_student_no").exists());

        mockMvc.perform(post("/api/app/trade/precheck")
                .header("Authorization", "Bearer " + unboundToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mobile_bound").value(true));

        String conflictToken = appLogin("mock:wx_s4_conflict");
        mockMvc.perform(post("/api/app/auth/phone-authorize")
                .header("Authorization", "Bearer " + conflictToken)
                .header("X-Trace-Id", "trace-s4-phone-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"phone_code":"mock:13900000009"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        Integer conflictAuditCount = jdbcTemplate.queryForObject(
                """
                select count(*) from audit_operation_log
                where trace_id = 'trace-s4-phone-conflict'
                  and operation_module = 'STUDENT'
                  and operation_type = 'PHONE_MERGE_CONFLICT'
                  and result = 'FAILED'
                """,
                Integer.class);
        assertThat(conflictAuditCount).isEqualTo(1);

        mockMvc.perform(post("/api/app/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"wx_code":"mock:DEMO_DISABLED_STUDENT","source_channel":"S4_TEST"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void should_match_and_convert_paid_lead_by_mobile_idempotently_for_s5() throws Exception {
        String response = mockMvc.perform(post("/api/h5/lead/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"S5 前置匹配线索","mobile":"13900002006","source_code":"S4_MATCH","intent_course_id":null}
                    """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long leadId = extractLong(response, "lead_id");

        LeadPaidConversionCommand command = new LeadPaidConversionCommand(
            100000000302L,
            "13900002006",
            null,
            200000000601L,
            java.time.LocalDateTime.now()
        );
        LeadPaidConversionResponse first = leadApplicationService.convertPaidLead(command);
        LeadPaidConversionResponse second = leadApplicationService.convertPaidLead(command);

        assertThat(first.matched()).isTrue();
        assertThat(first.leadId()).isEqualTo(leadId);
        assertThat(first.idempotentHit()).isFalse();
        assertThat(second.idempotentHit()).isTrue();

        jdbcTemplate.queryForMap(
            """
            select status, student_id, converted_order_id
            from crm_lead
            where id = ?
            """,
            leadId
        ).forEach((column, value) -> {
            if ("STATUS".equalsIgnoreCase(column)) {
                assertThat(value).isEqualTo("CONVERTED");
            }
            if ("STUDENT_ID".equalsIgnoreCase(column)) {
                assertThat(value).isEqualTo(100000000302L);
            }
            if ("CONVERTED_ORDER_ID".equalsIgnoreCase(column)) {
                assertThat(value).isEqualTo(200000000601L);
            }
        });
    }

    private long createAdminLead(String token, String name, String mobile, long ownerUserId) throws Exception {
        String response = mockMvc.perform(post("/api/admin/leads")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "s4-lead-" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "mobile": "%s",
                      "source_channel": "PC_ADMIN",
                      "source_code": "S4",
                      "owner_user_id": %d,
                      "next_follow_at": "2026-06-01T10:00:00",
                      "remark": "S4 lead"
                    }
                    """.formatted(name, mobile, ownerUserId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "lead_id");
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
