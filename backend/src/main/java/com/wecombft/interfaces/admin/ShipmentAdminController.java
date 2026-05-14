package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.fulfillment.SupplyChainApplicationService;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.ShipCommand;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.ShipmentActionResponse;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.ShipmentDetailResponse;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.ShipmentPage;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.SignCommand;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class ShipmentAdminController {

    private final SupplyChainApplicationService supplyChainApplicationService;

    public ShipmentAdminController(SupplyChainApplicationService supplyChainApplicationService) {
        this.supplyChainApplicationService = supplyChainApplicationService;
    }

    @GetMapping("/api/admin/shipments")
    @RequirePermission("fulfillment:shipment:write")
    public ResponseEntity<ApiResponse<ShipmentPage>> shipments(
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "order_no", required = false) String orderNo,
        @RequestParam(value = "tracking_no", required = false) String trackingNo,
        @RequestParam(value = "exception_flag", required = false) Boolean exceptionFlag,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            supplyChainApplicationService.shipments(status, orderNo, trackingNo, exceptionFlag, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/shipments/{shipment_id}")
    @RequirePermission("fulfillment:shipment:write")
    public ResponseEntity<ApiResponse<ShipmentDetailResponse>> shipmentDetail(
        @PathVariable("shipment_id") long shipmentId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            supplyChainApplicationService.shipmentDetail(shipmentId),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/shipments/{shipment_id}/ship")
    @RequirePermission("fulfillment:shipment:write")
    public ResponseEntity<ApiResponse<ShipmentActionResponse>> ship(
        @PathVariable("shipment_id") long shipmentId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ShipCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            supplyChainApplicationService.ship(AdminPrincipalContext.currentOrNull(), shipmentId, idempotencyKey, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/shipments/{shipment_id}/sign")
    @RequirePermission("fulfillment:shipment:write")
    public ResponseEntity<ApiResponse<ShipmentActionResponse>> sign(
        @PathVariable("shipment_id") long shipmentId,
        @RequestBody SignCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            supplyChainApplicationService.sign(AdminPrincipalContext.currentOrNull(), shipmentId, command),
            TraceIds.currentOrCreate()));
    }
}
