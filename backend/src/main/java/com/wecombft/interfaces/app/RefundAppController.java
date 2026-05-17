package com.wecombft.interfaces.app;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.CreationResult;
import com.wecombft.application.command.finance.RefundApplyCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.interfaces.dto.finance.RefundApplyRequest;
import com.wecombft.interfaces.dto.finance.RefundPage;
import com.wecombft.interfaces.dto.finance.RefundResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class RefundAppController {

    private final AfterSalesFinanceApplicationService service;

    public RefundAppController(AfterSalesFinanceApplicationService service) {
        this.service = service;
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
}
