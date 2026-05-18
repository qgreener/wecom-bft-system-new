package com.wecombft.interfaces.health;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.health.HealthReadinessService;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/health")
@EnableConfigurationProperties({
    AppProperties.class,
    FileStorageProperties.class,
    IntegrationModeProperties.class,
    RedisConnectionProperties.class,
    com.wecombft.infrastructure.config.WecomProperties.class,
    com.wecombft.infrastructure.config.WechatMiniappProperties.class,
    com.wecombft.infrastructure.config.WecomCallbackProperties.class,
    com.wecombft.infrastructure.config.WecomApprovalProperties.class
})
public class HealthController {

    private final AppProperties appProperties;
    private final IntegrationModeProperties integrationModeProperties;
    private final HealthReadinessService readinessService;

    public HealthController(
        AppProperties appProperties,
        IntegrationModeProperties integrationModeProperties,
        HealthReadinessService readinessService
    ) {
        this.appProperties = appProperties;
        this.integrationModeProperties = integrationModeProperties;
        this.readinessService = readinessService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthPayload>> health() {
        String status = appProperties.maintenanceMode() ? "MAINTENANCE" : "UP";
        HealthPayload payload = new HealthPayload(
            status,
            appProperties.name(),
            appProperties.version(),
            appProperties.environment(),
            integrationModeProperties.defaultMode(),
            OffsetDateTime.now(),
            TraceIds.currentOrCreate()
        );
        HttpStatus httpStatus = appProperties.maintenanceMode() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(httpStatus).body(ApiResponse.ok(payload, TraceIds.currentOrCreate()));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<ReadinessPayload>> ready() {
        List<DependencyCheckPayload> checks = readinessService.check();
        boolean ready = checks.stream().allMatch(check -> "UP".equals(check.status()));
        ReadinessPayload payload = new ReadinessPayload(
            ready ? "UP" : "DOWN",
            checks,
            OffsetDateTime.now(),
            TraceIds.currentOrCreate()
        );
        HttpStatus httpStatus = ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(ApiResponse.ok(payload, TraceIds.currentOrCreate()));
    }
}
