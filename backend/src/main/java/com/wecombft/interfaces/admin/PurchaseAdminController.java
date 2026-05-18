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

import com.wecombft.application.purchase.PurchaseApplicationService;
import com.wecombft.application.CreationResult;
import com.wecombft.interfaces.dto.purchase.PurchaseCreateRequest;
import com.wecombft.interfaces.dto.purchase.PurchaseInputInvoiceRequest;
import com.wecombft.interfaces.dto.purchase.PurchaseReceiveRequest;
import com.wecombft.interfaces.dto.purchase.PurchasePage;
import com.wecombft.interfaces.dto.purchase.PurchaseReceiptResponse;
import com.wecombft.interfaces.dto.purchase.PurchaseResponse;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class PurchaseAdminController {

    private final PurchaseApplicationService purchaseApplicationService;

    public PurchaseAdminController(PurchaseApplicationService purchaseApplicationService) {
        this.purchaseApplicationService = purchaseApplicationService;
    }

    @PostMapping("/api/admin/purchases")
    @RequirePermission("purchase:order:write")
    public ResponseEntity<ApiResponse<PurchaseResponse>> createPurchase(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PurchaseCreateRequest command
    ) {
        CreationResult<PurchaseResponse> result = purchaseApplicationService.createPurchase(
            AdminPrincipalContext.currentOrNull(),
            idempotencyKey,
            command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(result.created()
                ? ApiResponse.created(result.response(), TraceIds.currentOrCreate())
                : ApiResponse.ok(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/purchases")
    @RequirePermission("purchase:order:write")
    public ResponseEntity<ApiResponse<PurchasePage>> purchases(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.purchases(status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/purchases/{purchase_id}")
    @RequirePermission("purchase:order:write")
    public ResponseEntity<ApiResponse<PurchaseResponse>> purchaseDetail(
        @PathVariable("purchase_id") long purchaseId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.purchaseDetail(purchaseId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/purchases/{purchase_id}/receive")
    @RequirePermission("purchase:order:write")
    public ResponseEntity<ApiResponse<PurchaseReceiptResponse>> receive(
        @PathVariable("purchase_id") long purchaseId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PurchaseReceiveRequest command
    ) {
        CreationResult<PurchaseReceiptResponse> result = purchaseApplicationService.receivePurchase(
            AdminPrincipalContext.currentOrNull(),
            purchaseId,
            idempotencyKey,
            command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(result.created()
                ? ApiResponse.created(result.response(), TraceIds.currentOrCreate())
                : ApiResponse.ok(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/purchases/{purchase_id}/input-invoice")
    @RequirePermission("purchase:order:write")
    public ResponseEntity<ApiResponse<PurchaseResponse>> inputInvoice(
        @PathVariable("purchase_id") long purchaseId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody PurchaseInputInvoiceRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.backfillInputInvoice(
                AdminPrincipalContext.currentOrNull(),
                purchaseId,
                idempotencyKey,
                command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

}
