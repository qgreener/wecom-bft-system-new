package com.wecombft.interfaces.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
class AdminSecurityInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_require_login_for_new_admin_endpoint_even_without_manual_check() throws Exception {
        mockMvc.perform(get("/api/admin/test-guard/protected"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void should_allow_logged_in_admin_endpoint_without_manual_check() throws Exception {
        mockMvc.perform(get("/api/admin/test-guard/protected")
                .header("Authorization", "Bearer S3-DEMO-DEMO_ADMIN"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("reachable")));
    }

    @Test
    void should_enforce_permission_annotation_for_admin_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test-guard/audit-only")
                .header("Authorization", "Bearer S3-DEMO-DEMO_OPS"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/test-guard/audit-only")
                .header("Authorization", "Bearer S3-DEMO-DEMO_ADMIN"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("audit")));
    }

    @Test
    void should_enforce_any_permission_annotation_for_admin_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/test-guard/course-write")
                .header("Authorization", "Bearer S3-DEMO-DEMO_OPS"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/admin/test-guard/course-write")
                .header("Authorization", "Bearer S3-DEMO-DEMO_TEACHER"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("course")));
    }
}
