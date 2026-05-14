package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class SystemConfigAndAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_return_only_masked_system_configs() throws Exception {
        String adminToken = loginAs("DEMO_ADMIN");

        mockMvc.perform(get("/api/admin/system/configs")
                .header("Authorization", "Bearer " + adminToken)
                .param("config_group", "PAYMENT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.config_group").value("PAYMENT"))
            .andExpect(jsonPath("$.data.config_items[*].config_key", hasItem("WECHAT_PAY_API_V3_KEY")))
            .andExpect(jsonPath("$.data.config_items[*].masked_value", hasItem("********")))
            .andExpect(jsonPath("$.data.config_items[*].plain_value").doesNotExist())
            .andExpect(content().string(not(containsString("secret"))))
            .andExpect(content().string(not(containsString("MCH_ID"))));
    }

    @Test
    void should_save_config_without_storing_or_returning_plain_secret_and_write_audit_log() throws Exception {
        String adminToken = loginAs("DEMO_ADMIN");
        String fakeSecret = "placeholder-api-v3-value-for-test-only";

        mockMvc.perform(post("/api/admin/system/configs")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Trace-Id", "trace-config-save")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "config_group": "PAYMENT",
                      "change_reason": "S3 config masking verification",
                      "config_items": [
                        {"config_key": "INTEGRATION_PAYMENT_MODE", "config_value": "mock"},
                        {"config_key": "WECHAT_PAY_API_V3_KEY", "config_value": "%s"}
                      ]
                    }
                    """.formatted(fakeSecret)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.config_group").value("PAYMENT"))
            .andExpect(jsonPath("$.data.updated_keys", hasItem("WECHAT_PAY_API_V3_KEY")))
            .andExpect(jsonPath("$.data.audit_log_id").exists())
            .andExpect(content().string(not(containsString(fakeSecret))));

        String storedValue = jdbcTemplate.queryForObject(
                """
                select config_value from sys_config
                where config_group = 'PAYMENT' and config_key = 'WECHAT_PAY_API_V3_KEY'
                """,
                String.class);
        assertThat(storedValue).isNotEqualTo(fakeSecret);

        mockMvc.perform(get("/api/admin/audit/logs")
                .header("Authorization", "Bearer " + adminToken)
                .param("trace_id", "trace-config-save")
                .param("target_type", "SYS_CONFIG"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].trace_id", hasItem("trace-config-save")))
            .andExpect(jsonPath("$.data.records[*].operation_type", hasItem("CONFIG_SAVE")))
            .andExpect(content().string(not(containsString(fakeSecret))));
    }

    @Test
    void should_filter_audit_logs_by_trace_target_and_order_id() throws Exception {
        String adminToken = loginAs("DEMO_ADMIN");
        Long id = System.nanoTime();
        Long targetId = id + 1;
        Long orderId = id + 2;
        jdbcTemplate.update(
                """
                insert into audit_operation_log (
                    id, trace_id, operator_user_id, operator_name, operation_module, operation_type,
                    target_type, target_id, target_no, order_id, before_snapshot, after_snapshot,
                    result, failure_reason, client_ip, user_agent, occurred_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, ?, null, ?, ?, ?)
                """,
                id,
                "trace-order-audit",
                100000000001L,
                "S3 Admin",
                "TRADE",
                "ORDER_TRACE_TEST",
                "TRADE_ORDER",
                targetId,
                "ORD_S3_TRACE",
                orderId,
                "{\"mobile\":\"139****0000\",\"token\":\"********\"}",
                "SUCCESS",
                "127.0.0.1",
                "S3Test",
                LocalDateTime.now());

        mockMvc.perform(get("/api/admin/audit/logs")
                .header("Authorization", "Bearer " + adminToken)
                .param("trace_id", "trace-order-audit")
                .param("target_type", "TRADE_ORDER")
                .param("target_id", targetId.toString())
                .param("order_id", orderId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].target_no", hasItem("ORD_S3_TRACE")))
            .andExpect(content().string(not(containsString("13900000000"))))
            .andExpect(content().string(not(containsString("token-value"))));
    }

    @Test
    void should_forbid_non_admin_from_saving_system_config_or_reading_audit_logs() throws Exception {
        String opsToken = loginAs("DEMO_OPS");

        mockMvc.perform(post("/api/admin/system/configs")
                .header("Authorization", "Bearer " + opsToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"config_group":"PAYMENT","change_reason":"not allowed","config_items":[]}
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit/logs")
                .header("Authorization", "Bearer " + opsToken))
            .andExpect(status().isForbidden());
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
