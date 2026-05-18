package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.interfaces.dto.finance.CompensationActionRequest;
import com.wecombft.interfaces.dto.finance.CompensationRetryResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class CompensationAdminController {

    private final AfterSalesFinanceApplicationService service;

    public CompensationAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/collab/compensations/{task_id}/actions")
    @RequireAnyPermission({"refund:review:write", "tax:invoice:write", "accounting:material:write", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<CompensationRetryResponse>> compensationAction(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("task_id") long taskId,
        @RequestBody CompensationActionRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.compensationAction(AdminPrincipalContext.currentOrNull(), idempotencyKey, taskId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
