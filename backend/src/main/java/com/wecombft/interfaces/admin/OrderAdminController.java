package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.trade.OrderPaymentApplicationService;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderDetailResponse;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderPage;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class OrderAdminController {

    private final OrderPaymentApplicationService orderPaymentApplicationService;

    public OrderAdminController(OrderPaymentApplicationService orderPaymentApplicationService) {
        this.orderPaymentApplicationService = orderPaymentApplicationService;
    }

    @GetMapping("/api/admin/orders")
    @RequireAnyPermission({"trade:order:read", "refund:review:write", "fulfillment:shipment:write", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<OrderPage>> orders(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "student_id", required = false) Long studentId,
        @RequestParam(value = "payment_status", required = false) String paymentStatus,
        @RequestParam(value = "fulfillment_status", required = false) String fulfillmentStatus,
        @RequestParam(value = "refund_status", required = false) String refundStatus,
        @RequestParam(value = "invoice_status", required = false) String invoiceStatus,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.adminOrders(
                AdminPrincipalContext.currentOrNull(),
                keyword,
                studentId,
                paymentStatus,
                fulfillmentStatus,
                refundStatus,
                invoiceStatus,
                pageNo,
                pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/orders/{order_id}")
    @RequireAnyPermission({"trade:order:read", "refund:review:write", "fulfillment:shipment:write", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<OrderDetailResponse>> detail(@PathVariable("order_id") long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.adminOrderDetail(AdminPrincipalContext.currentOrNull(), orderId),
            TraceIds.currentOrCreate()));
    }
}
