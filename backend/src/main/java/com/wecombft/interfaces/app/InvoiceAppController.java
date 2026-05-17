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
import com.wecombft.application.command.finance.InvoiceApplyCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.interfaces.dto.finance.InvoiceApplyRequest;
import com.wecombft.interfaces.dto.finance.InvoicePage;
import com.wecombft.interfaces.dto.finance.InvoiceResponse;
import com.wecombft.interfaces.dto.finance.InvoiceTitlePage;
import com.wecombft.interfaces.dto.finance.InvoiceTitleRequest;
import com.wecombft.interfaces.dto.finance.InvoiceTitleResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class InvoiceAppController {

    private final AfterSalesFinanceApplicationService service;

    public InvoiceAppController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/app/invoice-titles")
    public ResponseEntity<ApiResponse<InvoiceTitleResponse>> saveInvoiceTitle(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody InvoiceTitleRequest command
    ) {
        CreationResult<InvoiceTitleResponse> result = service.saveInvoiceTitle(authorization, idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/invoice-titles")
    public ResponseEntity<ApiResponse<InvoiceTitlePage>> invoiceTitles(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(service.invoiceTitles(authorization), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/orders/{order_id}/invoices")
    public ResponseEntity<ApiResponse<InvoiceResponse>> applyOrderInvoice(
        @RequestHeader("Authorization") String authorization,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("order_id") long orderId,
        @RequestBody InvoiceApplyRequest command
    ) {
        InvoiceApplyCommand merged = command == null
            ? new InvoiceApplyCommand(orderId, null, null)
            : new InvoiceApplyCommand(orderId, command.titleId(), command.email());
        CreationResult<InvoiceResponse> result = service.applyInvoice(authorization, idempotencyKey, merged);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/app/invoices")
    public ResponseEntity<ApiResponse<InvoicePage>> appInvoices(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(service.appInvoices(authorization), TraceIds.currentOrCreate()));
    }
}
