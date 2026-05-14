package com.wecombft.interfaces.callback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.fulfillment.FulfillmentApplicationService;
import com.wecombft.application.fulfillment.command.LogisticsTraceCommand;
import com.wecombft.application.fulfillment.response.LogisticsCallbackResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class LogisticsCallbackController {

    private final FulfillmentApplicationService fulfillmentApplicationService;

    public LogisticsCallbackController(FulfillmentApplicationService fulfillmentApplicationService) {
        this.fulfillmentApplicationService = fulfillmentApplicationService;
    }

    @PostMapping("/api/callbacks/logistics/traces")
    public ResponseEntity<ApiResponse<LogisticsCallbackResponse>> trace(@RequestBody LogisticsTraceCommand command) {
        LogisticsCallbackResponse response = fulfillmentApplicationService.handleLogisticsCallback(command);
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }
}
