package com.wecombft.interfaces.health;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_return_non_sensitive_health_payload() throws Exception {
        mockMvc.perform(get("/api/health").header("X-Trace-Id", "trace-from-test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.message").value("ok"))
            .andExpect(jsonPath("$.trace_id").value("trace-from-test"))
            .andExpect(jsonPath("$.data.status").value("UP"))
            .andExpect(jsonPath("$.data.app_name").value("wecom-bft-new-system"))
            .andExpect(jsonPath("$.data.version").value("0.1.0-test"))
            .andExpect(jsonPath("$.data.server_time", matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*")))
            .andExpect(jsonPath("$.data.mysql_password").doesNotExist())
            .andExpect(jsonPath("$.data.security_jwt_secret").doesNotExist())
            .andExpect(jsonPath("$.data.wechat_pay_api_v3_key").doesNotExist());
    }
}
