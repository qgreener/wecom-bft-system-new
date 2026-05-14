package com.wecombft.interfaces.callback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.trade.OrderPaymentApplicationService;
import com.wecombft.interfaces.dto.trade.PaymentCallbackRequest;
import com.wecombft.interfaces.dto.trade.PaymentCallbackResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class PaymentCallbackController {

    private final OrderPaymentApplicationService orderPaymentApplicationService;

    public PaymentCallbackController(OrderPaymentApplicationService orderPaymentApplicationService) {
        this.orderPaymentApplicationService = orderPaymentApplicationService;
    }

    @PostMapping("/api/callbacks/payments/wechat")
    public ResponseEntity<ApiResponse<PaymentCallbackResponse>> wechatPayment(@RequestBody PaymentCallbackRequest command) {
        PaymentCallbackResponse response = orderPaymentApplicationService.handleWechatPaymentCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }
}
