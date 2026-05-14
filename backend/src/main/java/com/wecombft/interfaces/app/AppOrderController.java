package com.wecombft.interfaces.app;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.trade.OrderPaymentApplicationService;
import com.wecombft.application.trade.OrderPaymentApplicationService.CancelOrderCommand;
import com.wecombft.application.trade.OrderPaymentApplicationService.MockPayCommand;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderCloseResponse;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderConfirmCommand;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderConfirmResponse;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderCreateCommand;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderCreateResponse;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderDetailResponse;
import com.wecombft.application.trade.OrderPaymentApplicationService.OrderPage;
import com.wecombft.application.trade.OrderPaymentApplicationService.PayCommand;
import com.wecombft.application.trade.OrderPaymentApplicationService.PaymentCallbackResponse;
import com.wecombft.application.trade.OrderPaymentApplicationService.PaymentPrepareResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class AppOrderController {

    private final OrderPaymentApplicationService orderPaymentApplicationService;

    public AppOrderController(OrderPaymentApplicationService orderPaymentApplicationService) {
        this.orderPaymentApplicationService = orderPaymentApplicationService;
    }

    @PostMapping("/api/app/orders/confirm")
    public ResponseEntity<ApiResponse<OrderConfirmResponse>> confirm(
        @RequestHeader("Authorization") String authorization,
        @RequestBody OrderConfirmCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.confirm(authorization, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders")
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody OrderCreateCommand command
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(
            orderPaymentApplicationService.create(authorization, idempotencyKey, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders/{order_id}/pay")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> pay(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") long orderId,
        @RequestBody PayCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.preparePayment(authorization, orderId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders/{order_id}/cancel")
    public ResponseEntity<ApiResponse<OrderCloseResponse>> cancel(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") long orderId,
        @RequestBody CancelOrderCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.cancel(authorization, orderId, command),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders/{order_id}/mock-pay")
    public ResponseEntity<ApiResponse<PaymentCallbackResponse>> mockPay(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") long orderId,
        @RequestBody MockPayCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.mockPay(authorization, orderId, command),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/orders")
    public ResponseEntity<ApiResponse<OrderPage>> orders(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "payment_status", required = false) String paymentStatus,
        @RequestParam(value = "fulfillment_status", required = false) String fulfillmentStatus,
        @RequestParam(value = "refund_status", required = false) String refundStatus,
        @RequestParam(value = "invoice_status", required = false) String invoiceStatus,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.appOrders(
                authorization,
                paymentStatus,
                fulfillmentStatus,
                refundStatus,
                invoiceStatus,
                pageNo,
                pageSize),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/orders/{order_id}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> orderDetail(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") long orderId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            orderPaymentApplicationService.appOrderDetail(authorization, orderId),
            TraceIds.currentOrCreate()));
    }
}
