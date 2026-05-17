package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.interfaces.dto.finance.AccountingWorkbenchSummaryResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class AccountingWorkbenchAdminController {

    private final AfterSalesFinanceApplicationService service;

    public AccountingWorkbenchAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/accounting-workbench/summary")
    @RequireAnyPermission({"tax:invoice:write", "finance:reconciliation:write", "accounting:material:write"})
    public ResponseEntity<ApiResponse<AccountingWorkbenchSummaryResponse>> accountingWorkbenchSummary(
        @RequestParam(value = "related_month", required = false) String relatedMonth
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.accountingWorkbenchSummary(AdminPrincipalContext.currentOrNull(), relatedMonth),
            TraceIds.currentOrCreate()));
    }
}
