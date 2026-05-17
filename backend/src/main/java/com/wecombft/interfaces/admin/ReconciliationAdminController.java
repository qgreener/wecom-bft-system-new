package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.CreationResult;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchPage;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchResponse;
import com.wecombft.interfaces.dto.finance.ReconciliationCheckRequest;
import com.wecombft.interfaces.dto.finance.ReconciliationImportRequest;
import com.wecombft.interfaces.dto.finance.ReconciliationRecordResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class ReconciliationAdminController {

    private final AfterSalesFinanceApplicationService service;

    public ReconciliationAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/reconciliation/batches")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> createReconciliationBatch(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ReconciliationImportRequest command
    ) {
        CreationResult<ReconciliationBatchResponse> result = service.importReconciliation(
            AdminPrincipalContext.currentOrNull(), idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliation/batches")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchPage>> reconciliationBatches() {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationBatches(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliation/batches/{batch_id}")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> reconciliationBatchDetail(@PathVariable("batch_id") long batchId) {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationDetail(batchId), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliation/batches/{batch_id}/records")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> reconciliationBatchRecords(@PathVariable("batch_id") long batchId) {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationDetail(batchId), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/reconciliation/records/{reconciliation_id}/check")
    @RequireAnyPermission({"finance:reconciliation:check", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<ReconciliationRecordResponse>> checkReconciliationRecord(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("reconciliation_id") long reconciliationId,
        @RequestBody ReconciliationCheckRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.checkReconciliationRecord(
                AdminPrincipalContext.currentOrNull(),
                idempotencyKey,
                reconciliationId,
                command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
