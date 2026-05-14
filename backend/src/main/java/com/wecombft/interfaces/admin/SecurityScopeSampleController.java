package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.SecurityScopeSampleService;
import com.wecombft.application.iam.SecurityScopeSampleService.ScopeSampleResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/admin/security/scope-samples")
public class SecurityScopeSampleController {

    private final SecurityScopeSampleService securityScopeSampleService;

    public SecurityScopeSampleController(SecurityScopeSampleService securityScopeSampleService) {
        this.securityScopeSampleService = securityScopeSampleService;
    }

    @GetMapping("/{sample_id}")
    public ResponseEntity<ApiResponse<ScopeSampleResponse>> getSample(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("sample_id") String sampleId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            securityScopeSampleService.getSample(authorization, sampleId),
            TraceIds.currentOrCreate()));
    }
}
