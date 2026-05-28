package com.wecombft.interfaces.admin;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.report.ReportsApplicationService;
import com.wecombft.application.report.ReportsApplicationService.ConversionFunnel;
import com.wecombft.application.report.ReportsApplicationService.RevenueTrendPoint;
import com.wecombft.application.report.ReportsApplicationService.RiskAlerts;
import com.wecombft.application.report.ReportsApplicationService.SalesRanking;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/admin/reports")
public class ReportsAdminController {

    private final ReportsApplicationService service;

    public ReportsAdminController(ReportsApplicationService service) {
        this.service = service;
    }

    @GetMapping("/revenue-trend")
    @RequireAnyPermission({"report:business:read", "report:learning:read", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<RevenueTrendPoint>>> revenueTrend(
        @RequestParam(value = "days", required = false, defaultValue = "7") Integer days
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.revenueTrend(days), TraceIds.currentOrCreate()));
    }

    @GetMapping("/conversion-funnel")
    @RequireAnyPermission({"report:business:read", "report:learning:read", "system:audit:read"})
    public ResponseEntity<ApiResponse<ConversionFunnel>> conversionFunnel() {
        return ResponseEntity.ok(ApiResponse.ok(service.conversionFunnel(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/sales-ranking")
    @RequireAnyPermission({"report:business:read", "report:learning:read", "system:audit:read"})
    public ResponseEntity<ApiResponse<List<SalesRanking>>> salesRanking() {
        return ResponseEntity.ok(ApiResponse.ok(service.salesRanking(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/risk-alerts")
    @RequireAnyPermission({"report:business:read", "report:learning:read", "system:audit:read"})
    public ResponseEntity<ApiResponse<RiskAlerts>> riskAlerts() {
        return ResponseEntity.ok(ApiResponse.ok(service.riskAlerts(), TraceIds.currentOrCreate()));
    }

    /**
     * 一次性聚合首页用，避免前端发 4 次请求。
     */
    @GetMapping("/overview")
    @RequireAnyPermission({"report:business:read", "report:learning:read", "system:audit:read"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(
        @RequestParam(value = "days", required = false, defaultValue = "7") Integer days
    ) {
        Map<String, Object> body = Map.of(
            "revenue_trend", service.revenueTrend(days),
            "conversion_funnel", service.conversionFunnel(),
            "sales_ranking", service.salesRanking(),
            "risk_alerts", service.riskAlerts());
        return ResponseEntity.ok(ApiResponse.ok(body, TraceIds.currentOrCreate()));
    }
}
