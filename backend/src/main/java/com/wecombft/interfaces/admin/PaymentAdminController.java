package com.wecombft.interfaces.admin;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.trade.OrderPaymentApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.trade.PaymentAdminPage;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class PaymentAdminController {

    private final OrderPaymentApplicationService service;

    public PaymentAdminController(OrderPaymentApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/payments")
    @RequirePermission("payment:read")
    public ResponseEntity<ApiResponse<PaymentAdminPage>> adminPayments(
        @RequestParam(value = "order_id", required = false) Long orderId,
        @RequestParam(value = "merchant_order_no", required = false) String merchantOrderNo,
        @RequestParam(value = "payment_result", required = false) String paymentResult,
        @RequestParam(value = "paid_at_start", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidAtStart,
        @RequestParam(value = "paid_at_end", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime paidAtEnd,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.adminPayments(
                AdminPrincipalContext.currentOrNull(),
                orderId,
                merchantOrderNo,
                paymentResult,
                paidAtStart,
                paidAtEnd,
                pageNo,
                pageSize),
            TraceIds.currentOrCreate()));
    }
}
