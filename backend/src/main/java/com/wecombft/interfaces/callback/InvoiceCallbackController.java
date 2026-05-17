package com.wecombft.interfaces.callback;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.interfaces.dto.finance.InvoiceCallbackResponse;
import com.wecombft.interfaces.dto.finance.InvoiceIssueCallbackRequest;
import com.wecombft.interfaces.dto.finance.InvoiceRedReverseCallbackRequest;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class InvoiceCallbackController {

    private final AfterSalesFinanceApplicationService service;

    public InvoiceCallbackController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/callbacks/invoices/issue")
    public ResponseEntity<ApiResponse<InvoiceCallbackResponse>> invoiceIssueCallback(@RequestBody InvoiceIssueCallbackRequest command) {
        InvoiceCallbackResponse response = service.handleInvoiceIssueCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/callbacks/invoices/red-reverse")
    public ResponseEntity<ApiResponse<InvoiceCallbackResponse>> invoiceRedReverseCallback(@RequestBody InvoiceRedReverseCallbackRequest command) {
        InvoiceCallbackResponse response = service.handleInvoiceRedReverseCallback(command == null ? null : command.toCommand());
        HttpStatus status = "FAILED".equals(response.processingStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(response, TraceIds.currentOrCreate()));
    }
}
