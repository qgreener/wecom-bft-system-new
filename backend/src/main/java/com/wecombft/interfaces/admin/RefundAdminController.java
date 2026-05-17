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

import com.wecombft.application.command.finance.RefundApproveCommand;
import com.wecombft.application.command.finance.RefundRejectCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.finance.RefundManualCompleteRequest;
import com.wecombft.interfaces.dto.finance.RefundPage;
import com.wecombft.interfaces.dto.finance.RefundResponse;
import com.wecombft.interfaces.dto.finance.RefundRetryRequest;
import com.wecombft.interfaces.dto.finance.RefundReviewRequest;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiException;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class RefundAdminController {

    private final AfterSalesFinanceApplicationService service;

    public RefundAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
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

    @PostMapping("/api/admin/refunds/{refund_id}/retry")
    @RequireAnyPermission({"refund:retry:write", "refund:review:write"})
    public ResponseEntity<ApiResponse<RefundResponse>> retryRefund(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("refund_id") long refundId,
        @RequestBody RefundRetryRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.retryRefund(AdminPrincipalContext.currentOrNull(), idempotencyKey, refundId, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
