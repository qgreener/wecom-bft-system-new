package com.wecombft.interfaces.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.purchase.PurchaseApplicationService;
import com.wecombft.interfaces.dto.purchase.SupplierConfirmRequest;
import com.wecombft.interfaces.dto.purchase.SupplierLogisticsRequest;
import com.wecombft.interfaces.dto.purchase.SupplierRejectRequest;
import com.wecombft.interfaces.dto.purchase.PurchasePage;
import com.wecombft.interfaces.dto.purchase.PurchaseResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class SupplierPurchaseController {

    private final PurchaseApplicationService purchaseApplicationService;

    public SupplierPurchaseController(PurchaseApplicationService purchaseApplicationService) {
        this.purchaseApplicationService = purchaseApplicationService;
    }

    @GetMapping("/api/supplier-h5/purchases")
    public ResponseEntity<ApiResponse<PurchasePage>> purchases(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.supplierPurchases(authorization, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/supplier-h5/purchases/{purchase_id}")
    public ResponseEntity<ApiResponse<PurchaseResponse>> detail(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("purchase_id") long purchaseId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.supplierPurchaseDetail(authorization, purchaseId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/supplier-h5/purchases/{purchase_id}/confirm")
    public ResponseEntity<ApiResponse<PurchaseResponse>> confirm(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("purchase_id") long purchaseId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody SupplierConfirmRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.supplierConfirm(authorization, purchaseId, idempotencyKey, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/supplier-h5/purchases/{purchase_id}/reject")
    public ResponseEntity<ApiResponse<PurchaseResponse>> reject(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("purchase_id") long purchaseId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody SupplierRejectRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.supplierReject(authorization, purchaseId, idempotencyKey, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/supplier-h5/purchases/{purchase_id}/logistics")
    public ResponseEntity<ApiResponse<PurchaseResponse>> logistics(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("purchase_id") long purchaseId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody SupplierLogisticsRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            purchaseApplicationService.supplierLogistics(authorization, purchaseId, idempotencyKey, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
