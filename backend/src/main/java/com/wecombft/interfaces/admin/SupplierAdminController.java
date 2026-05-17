package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.purchase.SupplierApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.supplier.SupplierPage;
import com.wecombft.interfaces.dto.supplier.SupplierResponse;
import com.wecombft.interfaces.dto.supplier.SupplierSaveRequest;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class SupplierAdminController {

    private final SupplierApplicationService service;

    public SupplierAdminController(SupplierApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/suppliers")
    @RequirePermission("supplier:read")
    public ResponseEntity<ApiResponse<SupplierPage>> adminSuppliers(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "access_status", required = false) String accessStatus,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminSuppliers(AdminPrincipalContext.currentOrNull(), keyword, accessStatus, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/suppliers")
    @RequirePermission("supplier:write")
    public ResponseEntity<ApiResponse<SupplierResponse>> saveSupplier(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody SupplierSaveRequest command
    ) {
        SupplierResponse response = service.saveSupplier(
            AdminPrincipalContext.currentOrNull(),
            idempotencyKey,
            command == null ? null : command.toCommand());
        HttpStatus status = command != null && command.supplierId() == null ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(response, TraceIds.currentOrCreate()));
    }
}
