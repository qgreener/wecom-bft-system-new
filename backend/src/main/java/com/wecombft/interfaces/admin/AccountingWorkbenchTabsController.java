package com.wecombft.interfaces.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.report.AccountingWorkbenchService;
import com.wecombft.application.report.AccountingWorkbenchService.IncomeRow;
import com.wecombft.application.report.AccountingWorkbenchService.InvoiceIssuedRow;
import com.wecombft.application.report.AccountingWorkbenchService.InvoicePendingRow;
import com.wecombft.application.report.AccountingWorkbenchService.PurchaseRow;
import com.wecombft.application.report.AccountingWorkbenchService.RefundRow;
import com.wecombft.application.report.AccountingWorkbenchService.TaxBurdenOverview;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/admin/accounting-workbench")
public class AccountingWorkbenchTabsController {

    private final AccountingWorkbenchService service;

    public AccountingWorkbenchTabsController(AccountingWorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/income")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<IncomeRow>>> income(@RequestParam("related_month") String month) {
        return ResponseEntity.ok(ApiResponse.ok(service.income(month), TraceIds.currentOrCreate()));
    }

    @GetMapping("/refunds")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<RefundRow>>> refunds(@RequestParam("related_month") String month) {
        return ResponseEntity.ok(ApiResponse.ok(service.refunds(month), TraceIds.currentOrCreate()));
    }

    @GetMapping("/purchases")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<PurchaseRow>>> purchases(@RequestParam("related_month") String month) {
        return ResponseEntity.ok(ApiResponse.ok(service.purchases(month), TraceIds.currentOrCreate()));
    }

    @GetMapping("/pending-invoices")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<InvoicePendingRow>>> pending() {
        return ResponseEntity.ok(ApiResponse.ok(service.pendingInvoices(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/issued-invoices")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<InvoiceIssuedRow>>> issued(@RequestParam("related_month") String month) {
        return ResponseEntity.ok(ApiResponse.ok(service.issuedInvoices(month), TraceIds.currentOrCreate()));
    }

    @GetMapping("/tax-burden")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<TaxBurdenOverview>> taxBurden(@RequestParam("related_month") String month) {
        return ResponseEntity.ok(ApiResponse.ok(service.taxBurden(month), TraceIds.currentOrCreate()));
    }

    /**
     * 一次性聚合所有 Tab 数据，便于前端做工作台展示。
     */
    @GetMapping("/tabs")
    @RequireAnyPermission({"accounting:material:write", "tax:invoice:write", "system:audit:read"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> tabs(@RequestParam("related_month") String month) {
        Map<String, Object> body = Map.of(
            "income", service.income(month),
            "refunds", service.refunds(month),
            "purchases", service.purchases(month),
            "pending_invoices", service.pendingInvoices(),
            "issued_invoices", service.issuedInvoices(month),
            "tax_burden", service.taxBurden(month));
        return ResponseEntity.ok(ApiResponse.ok(body, TraceIds.currentOrCreate()));
    }
}
