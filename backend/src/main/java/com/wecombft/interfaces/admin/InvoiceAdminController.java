package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.finance.InvoiceIssueRequest;
import com.wecombft.interfaces.dto.finance.InvoicePage;
import com.wecombft.interfaces.dto.finance.InvoiceRedReverseRequest;
import com.wecombft.interfaces.dto.finance.InvoiceResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class InvoiceAdminController {

    private final AfterSalesFinanceApplicationService service;

    public InvoiceAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/invoices")
    @RequireAnyPermission({"tax:invoice:write", "invoice:read"})
    public ResponseEntity<ApiResponse<InvoicePage>> adminInvoices(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "order_no", required = false) String orderNo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminInvoices(AdminPrincipalContext.currentOrNull(), status, orderNo),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/invoices/{invoice_id}")
    @RequireAnyPermission({"tax:invoice:write", "invoice:read"})
    public ResponseEntity<ApiResponse<InvoiceResponse>> adminInvoiceDetail(@PathVariable("invoice_id") long invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminInvoiceDetail(AdminPrincipalContext.currentOrNull(), invoiceId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/invoices/{invoice_id}/issue-manual")
    @RequirePermission("tax:invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issueInvoiceManual(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("invoice_id") long invoiceId,
        @RequestBody InvoiceIssueRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.issueInvoice(AdminPrincipalContext.currentOrNull(), idempotencyKey, invoiceId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/invoices/{invoice_id}/red-reverse")
    @RequirePermission("tax:invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> redReverseInvoice(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("invoice_id") long invoiceId,
        @RequestBody InvoiceRedReverseRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.redReverseInvoice(AdminPrincipalContext.currentOrNull(), idempotencyKey, invoiceId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
