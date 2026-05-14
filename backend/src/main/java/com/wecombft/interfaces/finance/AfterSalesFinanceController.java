package com.wecombft.interfaces.finance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.AccountingMaterialConfirmCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.AccountingMaterialCreateCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.AccountingMaterialDownloadResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.AccountingMaterialResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.AccountingMaterialUploadCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.CompensationRetryCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.CompensationRetryResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.CreationResult;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceApplyCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceCallbackResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceIssueCallbackCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceIssueCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoicePage;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceRedReverseCallbackCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceRedReverseCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceTitleCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceTitlePage;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.InvoiceTitleResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.ReconciliationBatchPage;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.ReconciliationBatchResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.ReconciliationImportCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundApplyCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundApproveCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundCallbackCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundCallbackResponse;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundManualCompleteCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundPage;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundRejectCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService.RefundResponse;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class AfterSalesFinanceController {

    private final AfterSalesFinanceApplicationService service;

    public AfterSalesFinanceController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/app/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> applyRefund(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody RefundApplyCommand command
    ) {
        CreationResult<RefundResponse> result = service.applyRefund(authorization, idempotencyKey, command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/refunds")
    public ResponseEntity<ApiResponse<RefundPage>> appRefunds(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(service.appRefunds(authorization), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/refunds/{refund_id}")
    public ResponseEntity<ApiResponse<RefundResponse>> appRefundDetail(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("refund_id") long refundId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.appRefundDetail(authorization, refundId), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/refunds/{refund_id}/approve")
    @RequirePermission("refund:review:write")
    public ResponseEntity<ApiResponse<RefundResponse>> approveRefund(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundApproveCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.approveRefund(AdminPrincipalContext.currentOrNull(), idempotencyKey, refundId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/refunds/{refund_id}/reject")
    @RequirePermission("refund:review:write")
    public ResponseEntity<ApiResponse<RefundResponse>> rejectRefund(
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundRejectCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.rejectRefund(AdminPrincipalContext.currentOrNull(), refundId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/refunds/{refund_id}/manual-complete")
    @RequireAnyPermission({"refund:review:write", "accounting:material:write"})
    public ResponseEntity<ApiResponse<RefundResponse>> manualCompleteRefund(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundManualCompleteCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.manualCompleteRefund(AdminPrincipalContext.currentOrNull(), idempotencyKey, refundId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/callbacks/refunds/wechat")
    public ResponseEntity<ApiResponse<RefundCallbackResponse>> refundCallback(@RequestBody RefundCallbackCommand command) {
        RefundCallbackResponse response = service.handleWechatRefundCallback(command);
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/invoice-titles")
    public ResponseEntity<ApiResponse<InvoiceTitleResponse>> saveInvoiceTitle(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InvoiceTitleCommand command
    ) {
        CreationResult<InvoiceTitleResponse> result = service.saveInvoiceTitle(authorization, idempotencyKey, command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/invoice-titles")
    public ResponseEntity<ApiResponse<InvoiceTitlePage>> invoiceTitles(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(service.invoiceTitles(authorization), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/invoices")
    public ResponseEntity<ApiResponse<InvoiceResponse>> applyInvoice(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InvoiceApplyCommand command
    ) {
        CreationResult<InvoiceResponse> result = service.applyInvoice(authorization, idempotencyKey, command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/invoices")
    public ResponseEntity<ApiResponse<InvoicePage>> appInvoices(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(service.appInvoices(authorization), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/invoices/{invoice_id}/issue")
    @RequirePermission("tax:invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issueInvoice(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("invoice_id") long invoiceId,
        @RequestBody InvoiceIssueCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.issueInvoice(AdminPrincipalContext.currentOrNull(), idempotencyKey, invoiceId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/invoices/{invoice_id}/red-reverse")
    @RequirePermission("tax:invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> redReverseInvoice(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("invoice_id") long invoiceId,
        @RequestBody InvoiceRedReverseCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.redReverseInvoice(AdminPrincipalContext.currentOrNull(), idempotencyKey, invoiceId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/callbacks/invoices/issue")
    public ResponseEntity<ApiResponse<InvoiceCallbackResponse>> invoiceIssueCallback(@RequestBody InvoiceIssueCallbackCommand command) {
        InvoiceCallbackResponse response = service.handleInvoiceIssueCallback(command);
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/callbacks/invoices/red-reverse")
    public ResponseEntity<ApiResponse<InvoiceCallbackResponse>> invoiceRedReverseCallback(@RequestBody InvoiceRedReverseCallbackCommand command) {
        InvoiceCallbackResponse response = service.handleInvoiceRedReverseCallback(command);
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/reconciliations/import")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> importReconciliation(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ReconciliationImportCommand command
    ) {
        CreationResult<ReconciliationBatchResponse> result = service.importReconciliation(AdminPrincipalContext.currentOrNull(), idempotencyKey, command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliations")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchPage>> reconciliationBatches() {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationBatches(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliations/{batch_id}")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> reconciliationDetail(@PathVariable("batch_id") long batchId) {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationDetail(batchId), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting-materials")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> createAccountingMaterial(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody AccountingMaterialCreateCommand command
    ) {
        CreationResult<AccountingMaterialResponse> result = service.createAccountingMaterial(AdminPrincipalContext.currentOrNull(), idempotencyKey, command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting-materials/{material_id}/files")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> uploadAccountingMaterialFiles(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("material_id") long materialId,
        @RequestBody AccountingMaterialUploadCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.uploadAccountingMaterialFiles(AdminPrincipalContext.currentOrNull(), idempotencyKey, materialId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting-materials/{material_id}/confirm")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> confirmAccountingMaterial(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("material_id") long materialId,
        @RequestBody AccountingMaterialConfirmCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.confirmAccountingMaterial(AdminPrincipalContext.currentOrNull(), idempotencyKey, materialId, command),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/accounting-materials/{material_id}/download")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialDownloadResponse>> downloadAccountingMaterial(
        @PathVariable("material_id") long materialId,
        @RequestParam("file_no") String fileNo,
        @RequestParam(value = "download_reason", required = false) String downloadReason
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.downloadAccountingMaterial(AdminPrincipalContext.currentOrNull(), materialId, fileNo, downloadReason),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/collab/compensations/{compensation_id}/retry")
    @RequireAnyPermission({"refund:review:write", "tax:invoice:write", "accounting:material:write", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<CompensationRetryResponse>> retryCompensation(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("compensation_id") long compensationId,
        @RequestBody CompensationRetryCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.retryCompensation(AdminPrincipalContext.currentOrNull(), idempotencyKey, compensationId, command),
            TraceIds.currentOrCreate()));
    }
}
