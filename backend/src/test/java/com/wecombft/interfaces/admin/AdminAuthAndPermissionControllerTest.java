package com.wecombft.interfaces.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthAndPermissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void should_allow_unassigned_user_only_to_role_application_entry() throws Exception {
        String token = loginAs("DEMO_UNASSIGNED");

        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_no").value("DEMO_UNASSIGNED"))
            .andExpect(jsonPath("$.data.roles").isEmpty())
            .andExpect(jsonPath("$.data.permission_codes", hasItem("iam:role-application:create")))
            .andExpect(jsonPath("$.data.permission_codes", not(hasItem("system:config:write"))))
            .andExpect(jsonPath("$.data.data_scope.scope_code").value("NONE"))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("role.application")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", not(hasItem("system.settings"))));

        mockMvc.perform(get("/api/admin/system/configs")
                .header("Authorization", "Bearer " + token)
                .param("config_group", "PAYMENT"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void should_return_role_specific_permissions_data_scope_and_field_masks() throws Exception {
        assertRoleView(
                "DEMO_ADMIN",
                "SUPER_ADMIN",
                "ALL",
                "system:config:write",
                "system.settings",
                "PLAIN");
        assertRoleView(
                "DEMO_OPS",
                "OPS",
                "OWN_OR_TEAM",
                "crm:lead:write",
                "crm.leads",
                "OWN_FULL_OTHER_MASK");
        assertRoleView(
                "DEMO_WAREHOUSE",
                "WAREHOUSE",
                "SUPPLY_CHAIN",
                "fulfillment:shipment:write",
                "fulfillment.shipments",
                "SHIPPING_ONLY_FULL");
        assertRoleView(
                "DEMO_ACCOUNTING",
                "ACCOUNTING",
                "FINANCE_AUTHORIZED",
                "tax:invoice:write",
                "accounting.workspace",
                "FINANCE_ONLY_FULL");
    }

    @Test
    void should_expose_order_menu_for_order_read_roles() throws Exception {
        assertOrderMenu("DEMO_OPS");
        assertOrderMenu("DEMO_EDU_ADMIN");
        assertOrderMenu("DEMO_SERVICE");
        assertOrderMenu("DEMO_WAREHOUSE");
        assertOrderMenu("DEMO_ACCOUNTING");
    }

    @Test
    void should_expose_s8_core_business_menus_from_permission_return() throws Exception {
        String superAdminToken = loginAs("DEMO_ADMIN");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + superAdminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("refund.reviews")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("invoice.manage")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("finance.reconciliation")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("accounting.workspace")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("system.settings")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("system.audit-logs")));

        String serviceToken = loginAs("DEMO_SERVICE");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + serviceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("refund.reviews")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("invoice.readonly")));

        String accountingToken = loginAs("DEMO_ACCOUNTING");
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("invoice.manage")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("finance.reconciliation")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("accounting.workspace")));
    }

    private void assertOrderMenu(String userNo) throws Exception {
        String token = loginAs(userNo);
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.permission_codes", hasItem("trade:order:read")))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem("trade.orders")));
    }

    private void assertRoleView(
        String userNo,
        String roleCode,
        String dataScope,
        String permissionCode,
        String menuCode,
        String maskStrategy
    ) throws Exception {
        String token = loginAs(userNo);
        mockMvc.perform(get("/api/admin/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].role_code", hasItem(roleCode)))
            .andExpect(jsonPath("$.data.permission_codes", hasItem(permissionCode)))
            .andExpect(jsonPath("$.data.data_scope.scope_code").value(dataScope))
            .andExpect(jsonPath("$.data.menus[*].menu_code", hasItem(menuCode)))
            .andExpect(jsonPath("$.data.field_masks[*].mask_strategy", hasItem(maskStrategy)));
    }

    private String loginAs(String userNo) throws Exception {
        String response = mockMvc.perform(post("/api/admin/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"user_no":"%s"}
                    """.formatted(userNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.access_token").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

        int marker = response.indexOf("\"access_token\":\"");
        int start = marker + "\"access_token\":\"".length();
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
