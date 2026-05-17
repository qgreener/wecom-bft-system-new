package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.CreationResult;
import com.wecombft.application.command.finance.AccountingMaterialCloseCommand;
import com.wecombft.application.command.finance.AccountingMaterialConfirmCommand;
import com.wecombft.application.command.finance.AccountingMaterialUploadCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.finance.AccountingMaterialActionRequest;
import com.wecombft.interfaces.dto.finance.AccountingMaterialCreateRequest;
import com.wecombft.interfaces.dto.finance.AccountingMaterialDownloadResponse;
import com.wecombft.interfaces.dto.finance.AccountingMaterialPage;
import com.wecombft.interfaces.dto.finance.AccountingMaterialResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiException;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class AccountingMaterialAdminController {

    private final AfterSalesFinanceApplicationService service;

    public AccountingMaterialAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/accounting/materials")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialPage>> accountingMaterials(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "related_month", required = false) String relatedMonth
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.accountingMaterials(AdminPrincipalContext.currentOrNull(), status, relatedMonth),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/accounting/materials/{material_id}")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> accountingMaterialDetail(
        @PathVariable("material_id") long materialId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.accountingMaterialDetail(AdminPrincipalContext.currentOrNull(), materialId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/accounting/materials")
    @RequirePermission("accounting:material:write")
    public ResponseEntity<ApiResponse<AccountingMaterialResponse>> createAccountingMaterial(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody AccountingMaterialCreateRequest command
    ) {
        CreationResult<AccountingMaterialResponse> result = service.createAccountingMaterial(
            AdminPrincipalContext.currentOrNull(), idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
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

    @GetMapping("/api/admin/accounting/materials/{material_id}/download")
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
}
