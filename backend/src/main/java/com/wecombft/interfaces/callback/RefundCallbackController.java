package com.wecombft.interfaces.callback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.interfaces.dto.finance.RefundCallbackRequest;
import com.wecombft.interfaces.dto.finance.RefundCallbackResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class RefundCallbackController {

    private final AfterSalesFinanceApplicationService service;

    public RefundCallbackController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/callbacks/refunds/wechat")
    public ResponseEntity<ApiResponse<RefundCallbackResponse>> refundCallback(@RequestBody RefundCallbackRequest command) {
        RefundCallbackResponse response = service.handleWechatRefundCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }
}
