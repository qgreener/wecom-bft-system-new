package com.wecombft.interfaces.callback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.fulfillment.SupplyChainApplicationService;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.LogisticsCallbackResponse;
import com.wecombft.application.fulfillment.SupplyChainApplicationService.LogisticsTraceCommand;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class LogisticsCallbackController {

    private final SupplyChainApplicationService supplyChainApplicationService;

    public LogisticsCallbackController(SupplyChainApplicationService supplyChainApplicationService) {
        this.supplyChainApplicationService = supplyChainApplicationService;
    }

    @PostMapping("/api/callbacks/logistics/traces")
    public ResponseEntity<ApiResponse<LogisticsCallbackResponse>> trace(@RequestBody LogisticsTraceCommand command) {
        LogisticsCallbackResponse response = supplyChainApplicationService.handleLogisticsCallback(command);
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }
}
