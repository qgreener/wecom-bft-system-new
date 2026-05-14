package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.system.SystemConfigService;
import com.wecombft.application.system.SystemConfigService.ConfigGroupResponse;
import com.wecombft.application.system.SystemConfigService.SaveConfigCommand;
import com.wecombft.application.system.SystemConfigService.SaveConfigResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/admin/system/configs")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ConfigGroupResponse>> query(
        @RequestHeader("Authorization") String authorization,
        @RequestParam("config_group") String configGroup
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            systemConfigService.query(authorization, configGroup),
            TraceIds.currentOrCreate()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SaveConfigResponse>> save(
        @RequestHeader("Authorization") String authorization,
        @RequestBody SaveConfigCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            systemConfigService.save(authorization, command),
            TraceIds.currentOrCreate()));
    }
}
