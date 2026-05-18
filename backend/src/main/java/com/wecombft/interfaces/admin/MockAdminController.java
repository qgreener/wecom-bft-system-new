package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.MockSceneApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.mock.MockScenesResponse;
import com.wecombft.interfaces.dto.mock.MockTriggerRequest;
import com.wecombft.interfaces.dto.mock.MockTriggerResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class MockAdminController {

    private final MockSceneApplicationService service;

    public MockAdminController(MockSceneApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/mock/scenes")
    @RequirePermission("mock:scene:read")
    public ResponseEntity<ApiResponse<MockScenesResponse>> listScenes(
        @RequestParam(value = "capability", required = false) String capability,
        @RequestParam(value = "enabled_only", required = false) Boolean enabledOnly
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.listScenes(AdminPrincipalContext.currentOrNull(), capability, enabledOnly),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/mock/scenes/{scene}/trigger")
    @RequirePermission("mock:scene:trigger")
    public ResponseEntity<ApiResponse<MockTriggerResponse>> triggerScene(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("scene") String scene,
        @RequestBody(required = false) MockTriggerRequest request
    ) {
        return ResponseEntity.accepted().body(ApiResponse.ok(
            service.triggerScene(AdminPrincipalContext.currentOrNull(), scene, idempotencyKey, request),
            TraceIds.currentOrCreate()));
    }
}
