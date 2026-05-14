package com.wecombft.interfaces.supplier;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.ExternalAuthApplicationService;
import com.wecombft.application.iam.ExternalAuthApplicationService.SupplierH5TokenCommand;
import com.wecombft.application.iam.ExternalAuthApplicationService.SupplierH5TokenResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/supplier-h5/auth")
public class SupplierH5AuthController {

    private final ExternalAuthApplicationService externalAuthApplicationService;

    public SupplierH5AuthController(ExternalAuthApplicationService externalAuthApplicationService) {
        this.externalAuthApplicationService = externalAuthApplicationService;
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<SupplierH5TokenResponse>> token(
        @RequestBody SupplierH5TokenCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            externalAuthApplicationService.supplierH5Token(command),
            TraceIds.currentOrCreate()));
    }
}
