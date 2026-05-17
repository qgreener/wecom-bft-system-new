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

import com.wecombft.application.command.finance.AccountingMaterialCloseCommand;
import com.wecombft.application.command.finance.AccountingMaterialConfirmCommand;
import com.wecombft.application.command.finance.AccountingMaterialUploadCommand;
import com.wecombft.application.command.finance.InvoiceApplyCommand;
import com.wecombft.application.command.finance.RefundApplyCommand;
import com.wecombft.application.command.finance.RefundApproveCommand;
import com.wecombft.application.command.finance.RefundRejectCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.interfaces.dto.finance.AccountingMaterialConfirmRequest;
import com.wecombft.interfaces.dto.finance.AccountingMaterialCloseRequest;
import com.wecombft.interfaces.dto.finance.AccountingMaterialCreateRequest;
import com.wecombft.interfaces.dto.finance.AccountingMaterialActionRequest;
import com.wecombft.interfaces.dto.finance.AccountingMaterialDownloadResponse;
import com.wecombft.interfaces.dto.finance.AccountingMaterialPage;
import com.wecombft.interfaces.dto.finance.AccountingMaterialResponse;
import com.wecombft.interfaces.dto.finance.AccountingMaterialUploadRequest;
import com.wecombft.interfaces.dto.finance.AccountingWorkbenchSummaryResponse;
import com.wecombft.interfaces.dto.finance.CompensationRetryRequest;
import com.wecombft.interfaces.dto.finance.CompensationRetryResponse;
import com.wecombft.application.CreationResult;
import com.wecombft.interfaces.dto.finance.InvoiceApplyRequest;
import com.wecombft.interfaces.dto.finance.InvoiceCallbackResponse;
import com.wecombft.interfaces.dto.finance.InvoiceIssueCallbackRequest;
import com.wecombft.interfaces.dto.finance.InvoiceIssueRequest;
import com.wecombft.interfaces.dto.finance.InvoicePage;
import com.wecombft.interfaces.dto.finance.InvoiceRedReverseCallbackRequest;
import com.wecombft.interfaces.dto.finance.InvoiceRedReverseRequest;
import com.wecombft.interfaces.dto.finance.InvoiceResponse;
import com.wecombft.interfaces.dto.finance.InvoiceTitleRequest;
import com.wecombft.interfaces.dto.finance.InvoiceTitlePage;
import com.wecombft.interfaces.dto.finance.InvoiceTitleResponse;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchPage;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchResponse;
import com.wecombft.interfaces.dto.finance.ReconciliationImportRequest;
import com.wecombft.interfaces.dto.finance.RefundApplyRequest;
import com.wecombft.interfaces.dto.finance.RefundApproveRequest;
import com.wecombft.interfaces.dto.finance.RefundCallbackRequest;
import com.wecombft.interfaces.dto.finance.RefundCallbackResponse;
import com.wecombft.interfaces.dto.finance.RefundManualCompleteRequest;
import com.wecombft.interfaces.dto.finance.RefundPage;
import com.wecombft.interfaces.dto.finance.RefundRejectRequest;
import com.wecombft.interfaces.dto.finance.RefundResponse;
import com.wecombft.interfaces.dto.finance.RefundReviewRequest;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiException;
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
        @RequestBody RefundApplyRequest command
    ) {
        CreationResult<RefundResponse> result = service.applyRefund(authorization, idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders/{order_id}/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> applyOrderRefund(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("order_id") long orderId,
        @RequestBody RefundApplyRequest command
    ) {
        RefundApplyCommand merged = command == null
            ? new RefundApplyCommand(orderId, null, null, null, null)
            : new RefundApplyCommand(
                orderId,
                command.applyAmountCent(),
                command.refundReason(),
                command.applyDescription(),
                command.entitlementAction());
        CreationResult<RefundResponse> result = service.applyRefund(authorization, idempotencyKey, merged);
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

    @GetMapping("/api/admin/refunds")
    @RequireAnyPermission({"refund:review:write", "accounting:material:write"})
    public ResponseEntity<ApiResponse<RefundPage>> adminRefunds(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "order_no", required = false) String orderNo
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminRefunds(AdminPrincipalContext.currentOrNull(), status, orderNo),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/refunds/{refund_id}")
    @RequireAnyPermission({"refund:review:write", "accounting:material:write"})
    public ResponseEntity<ApiResponse<RefundResponse>> adminRefundDetail(@PathVariable("refund_id") long refundId) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminRefundDetail(AdminPrincipalContext.currentOrNull(), refundId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/refunds/{refund_id}/approve")
    @RequirePermission("refund:review:write")
    public ResponseEntity<ApiResponse<RefundResponse>> approveRefund(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundApproveRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.approveRefund(AdminPrincipalContext.currentOrNull(), idempotencyKey, refundId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/refunds/{refund_id}/reject")
    @RequirePermission("refund:review:write")
    public ResponseEntity<ApiResponse<RefundResponse>> rejectRefund(
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundRejectRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.rejectRefund(AdminPrincipalContext.currentOrNull(), refundId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/refunds/{refund_id}/review")
    @RequirePermission("refund:review:write")
    public ResponseEntity<ApiResponse<RefundResponse>> reviewRefund(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundReviewRequest command
    ) {
        String action = command == null || command.action() == null ? "" : command.action().trim().toUpperCase();
        if ("APPROVE".equals(action)) {
            RefundApproveCommand approveCommand = new RefundApproveCommand(
                command.approvedAmountCent(),
                command.refundChannel(),
                command.reviewComment(),
                command.entitlementAction());
            return ResponseEntity.ok(ApiResponse.ok(
                service.approveRefund(AdminPrincipalContext.currentOrNull(), idempotencyKey, refundId, approveCommand),
                TraceIds.currentOrCreate()));
        }
        if ("REJECT".equals(action)) {
            return ResponseEntity.ok(ApiResponse.ok(
                service.rejectRefund(AdminPrincipalContext.currentOrNull(), refundId, new RefundRejectCommand(command.rejectReason())),
                TraceIds.currentOrCreate()));
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "退款审核动作非法");
    }

    @PostMapping("/api/admin/refunds/{refund_id}/manual-complete")
    @RequireAnyPermission({"refund:review:write", "accounting:material:write"})
    public ResponseEntity<ApiResponse<RefundResponse>> manualCompleteRefund(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundManualCompleteRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.manualCompleteRefund(AdminPrincipalContext.currentOrNull(), idempotencyKey, refundId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/callbacks/refunds/wechat")
    public ResponseEntity<ApiResponse<RefundCallbackResponse>> refundCallback(@RequestBody RefundCallbackRequest command) {
        RefundCallbackResponse response = service.handleWechatRefundCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/invoice-titles")
    public ResponseEntity<ApiResponse<InvoiceTitleResponse>> saveInvoiceTitle(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InvoiceTitleRequest command
    ) {
        CreationResult<InvoiceTitleResponse> result = service.saveInvoiceTitle(authorization, idempotencyKey, command == null ? null : command.toCommand());
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
        @RequestBody InvoiceApplyRequest command
    ) {
        CreationResult<InvoiceResponse> result = service.applyInvoice(authorization, idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders/{order_id}/invoices")
    public ResponseEntity<ApiResponse<InvoiceResponse>> applyOrderInvoice(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("order_id") long orderId,
        @RequestBody InvoiceApplyRequest command
    ) {
        InvoiceApplyCommand merged = command == null
            ? new InvoiceApplyCommand(orderId, null, null)
            : new InvoiceApplyCommand(orderId, command.titleId(), command.email());
        CreationResult<InvoiceResponse> result = service.applyInvoice(authorization, idempotencyKey, merged);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/invoices")
    public ResponseEntity<ApiResponse<InvoicePage>> appInvoices(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(service.appInvoices(authorization), TraceIds.currentOrCreate()));
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

    @PostMapping("/api/admin/invoices/{invoice_id}/issue")
    @RequirePermission("tax:invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issueInvoice(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("invoice_id") long invoiceId,
        @RequestBody InvoiceIssueRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.issueInvoice(AdminPrincipalContext.currentOrNull(), idempotencyKey, invoiceId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/invoices/{invoice_id}/issue-manual")
    @RequirePermission("tax:invoice:write")
    public ResponseEntity<ApiResponse<InvoiceResponse>> issueInvoiceManual(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("invoice_id") long invoiceId,
        @RequestBody InvoiceIssueRequest command
    ) {
        return issueInvoice(idempotencyKey, invoiceId, command);
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

    @PostMapping("/api/callbacks/invoices/issue")
    public ResponseEntity<ApiResponse<InvoiceCallbackResponse>> invoiceIssueCallback(@RequestBody InvoiceIssueCallbackRequest command) {
        InvoiceCallbackResponse response = service.handleInvoiceIssueCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/callbacks/invoices/red-reverse")
    public ResponseEntity<ApiResponse<InvoiceCallbackResponse>> invoiceRedReverseCallback(@RequestBody InvoiceRedReverseCallbackRequest command) {
        InvoiceCallbackResponse response = service.handleInvoiceRedReverseCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/reconciliations/import")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> importReconciliation(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ReconciliationImportRequest command
    ) {
        CreationResult<ReconciliationBatchResponse> result = service.importReconciliation(AdminPrincipalContext.currentOrNull(), idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/reconciliation/batches")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> createReconciliationBatch(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ReconciliationImportRequest command
    ) {
        return importReconciliation(idempotencyKey, command);
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

    @GetMapping("/api/admin/reconciliation/batches/{batch_id}/records")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> reconciliationBatchRecords(@PathVariable("batch_id") long batchId) {
        return reconciliationDetail(batchId);
    }

    @GetMapping("/api/admin/accounting-materials")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialPage>> accountingMaterials(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "related_month", required = false) String relatedMonth
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.accountingMaterials(AdminPrincipalContext.currentOrNull(), status, relatedMonth),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/accounting-materials/{material_id}")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> accountingMaterialDetail(
        @PathVariable("material_id") long materialId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.accountingMaterialDetail(AdminPrincipalContext.currentOrNull(), materialId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting-materials")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> createAccountingMaterial(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody AccountingMaterialCreateRequest command
    ) {
        CreationResult<AccountingMaterialResponse> result = service.createAccountingMaterial(AdminPrincipalContext.currentOrNull(), idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting/materials")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> createAccountingMaterialAlias(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody AccountingMaterialCreateRequest command
    ) {
        return createAccountingMaterial(idempotencyKey, command);
    }

    @PostMapping("/api/admin/accounting-materials/{material_id}/files")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> uploadAccountingMaterialFiles(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("material_id") long materialId,
        @RequestBody AccountingMaterialUploadRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.uploadAccountingMaterialFiles(AdminPrincipalContext.currentOrNull(), idempotencyKey, materialId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting-materials/{material_id}/confirm")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> confirmAccountingMaterial(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("material_id") long materialId,
        @RequestBody AccountingMaterialConfirmRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.confirmAccountingMaterial(AdminPrincipalContext.currentOrNull(), idempotencyKey, materialId, command == null ? null : command.toCommand()),
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

    @PostMapping("/api/admin/accounting-materials/{material_id}/close")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> closeAccountingMaterial(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("material_id") long materialId,
        @RequestBody AccountingMaterialCloseRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.closeAccountingMaterial(AdminPrincipalContext.currentOrNull(), idempotencyKey, materialId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting/materials/{material_id}/actions")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> accountingMaterialAction(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable("material_id") long materialId,
        @RequestBody AccountingMaterialActionRequest command
    ) {
        String action = command == null || command.action() == null ? "" : command.action().trim().toUpperCase();
        if ("UPLOAD".equals(action)) {
            return ResponseEntity.ok(ApiResponse.ok(
                service.uploadAccountingMaterialFiles(
                    AdminPrincipalContext.currentOrNull(),
                    idempotencyKey,
                    materialId,
                    new AccountingMaterialUploadCommand(command.fileRefs(), command.remark())),
                TraceIds.currentOrCreate()));
        }
        if ("CONFIRM".equals(action)) {
            return ResponseEntity.ok(ApiResponse.ok(
                service.confirmAccountingMaterial(
                    AdminPrincipalContext.currentOrNull(),
                    idempotencyKey,
                    materialId,
                    new AccountingMaterialConfirmCommand(command.remark())),
                TraceIds.currentOrCreate()));
        }
        if ("CLOSE".equals(action)) {
            return ResponseEntity.ok(ApiResponse.ok(
                service.closeAccountingMaterial(
                    AdminPrincipalContext.currentOrNull(),
                    idempotencyKey,
                    materialId,
                    new AccountingMaterialCloseCommand(command.closedReason())),
                TraceIds.currentOrCreate()));
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "代账材料动作非法");
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

    @PostMapping("/api/collab/compensations/{compensation_id}/retry")
    @RequireAnyPermission({"refund:review:write", "tax:invoice:write", "accounting:material:write", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<CompensationRetryResponse>> retryCompensation(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("compensation_id") long compensationId,
        @RequestBody CompensationRetryRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.retryCompensation(AdminPrincipalContext.currentOrNull(), idempotencyKey, compensationId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
