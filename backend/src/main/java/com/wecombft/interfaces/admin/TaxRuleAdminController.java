package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.TaxRuleApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.finance.TaxRulePage;
import com.wecombft.interfaces.dto.finance.TaxRuleResponse;
import com.wecombft.interfaces.dto.finance.TaxRuleSaveRequest;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class TaxRuleAdminController {

    private final TaxRuleApplicationService service;

    public TaxRuleAdminController(TaxRuleApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/tax-rules")
    @RequirePermission("tax:rule:write")
    public ResponseEntity<ApiResponse<TaxRulePage>> adminTaxRules(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminTaxRules(AdminPrincipalContext.currentOrNull(), status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/tax-rules")
    @RequirePermission("tax:rule:write")
    public ResponseEntity<ApiResponse<TaxRuleResponse>> saveTaxRule(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody TaxRuleSaveRequest command
    ) {
        TaxRuleResponse response = service.saveTaxRule(
            AdminPrincipalContext.currentOrNull(),
            idempotencyKey,
            command == null ? null : command.toCommand());
        HttpStatus status = command != null && command.ruleId() == null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(response, TraceIds.currentOrCreate()));
    }
}
