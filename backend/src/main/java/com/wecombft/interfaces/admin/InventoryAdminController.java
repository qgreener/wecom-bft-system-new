package com.wecombft.interfaces.admin;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.fulfillment.SupplyChainApplicationService;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.CreationResult;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.SkuCommand;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.SkuPage;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.SkuResponse;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.StockFlowCommand;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.StockFlowPage;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.StockFlowResponse;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class InventoryAdminController {

    private final SupplyChainApplicationService supplyChainApplicationService;

    public InventoryAdminController(SupplyChainApplicationService supplyChainApplicationService) {
        this.supplyChainApplicationService = supplyChainApplicationService;
    }

    @GetMapping("/api/admin/inventory/skus")
    @RequirePermission("inventory:sku:write")
    public ResponseEntity<ApiResponse<SkuPage>> skus(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "category_code", required = false) String categoryCode,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "warning_only", required = false) Boolean warningOnly,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            supplyChainApplicationService.skus(keyword, categoryCode, status, warningOnly, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/inventory/skus")
    @RequirePermission("inventory:sku:write")
    public ResponseEntity<ApiResponse<SkuResponse>> createSku(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody SkuCommand command
    ) {
        CreationResult<SkuResponse> result = supplyChainApplicationService.createSku(
            AdminPrincipalContext.currentOrNull(),
            idempotencyKey,
            command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(result.created()
                ? ApiResponse.created(result.response(), TraceIds.currentOrCreate())
                : ApiResponse.ok(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/inventory/stock-flows")
    @RequirePermission("inventory:sku:write")
    public ResponseEntity<ApiResponse<StockFlowPage>> stockFlows(
        @RequestParam(value = "sku_id", required = false) Long skuId,
        @RequestParam(value = "order_id", required = false) Long orderId,
        @RequestParam(value = "shipment_id", required = false) Long shipmentId,
        @RequestParam(value = "purchase_id", required = false) Long purchaseId,
        @RequestParam(value = "biz_type", required = false) String bizType,
        @RequestParam(value = "biz_id", required = false) Long bizId,
        @RequestParam(value = "occurred_at_start", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAtStart,
        @RequestParam(value = "occurred_at_end", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAtEnd,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            supplyChainApplicationService.stockFlows(
                skuId,
                orderId,
                shipmentId,
                purchaseId,
                bizType,
                bizId,
                occurredAtStart,
                occurredAtEnd,
                pageNo,
                pageSize),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/inventory/stock-flows")
    @RequirePermission("inventory:sku:write")
    public ResponseEntity<ApiResponse<StockFlowResponse>> createStockFlow(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody StockFlowCommand command
    ) {
        CreationResult<StockFlowResponse> result = supplyChainApplicationService.createStockFlow(
            AdminPrincipalContext.currentOrNull(),
            idempotencyKey,
            command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
            .body(result.created()
                ? ApiResponse.created(result.response(), TraceIds.currentOrCreate())
                : ApiResponse.ok(result.response(), TraceIds.currentOrCreate()));
    }
}
