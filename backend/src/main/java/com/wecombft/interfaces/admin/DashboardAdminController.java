package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.DashboardApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.interfaces.dto.dashboard.DashboardTodosResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class DashboardAdminController {

    private final DashboardApplicationService service;

    public DashboardAdminController(DashboardApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/dashboard/todos")
    @RequireAnyPermission({
        "trade:order:read",
        "refund:review:write",
        "tax:invoice:write",
        "finance:reconciliation:write",
        "accounting:material:write",
        "fulfillment:shipment:write",
        "purchase:order:write",
        "purchase:approval:approve"
    })
    public ResponseEntity<ApiResponse<DashboardTodosResponse>> todos() {
        return ResponseEntity.ok(ApiResponse.ok(
            service.todos(AdminPrincipalContext.currentOrNull()),
            TraceIds.currentOrCreate()));
    }
}
