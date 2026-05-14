package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.audit.AuditLogService.AuditPage;
import com.wecombft.infrastructure.persistence.audit.AuditLogRepository.AuditQuery;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/admin/audit/logs")
public class AuditLogController {

    private final AdminSessionService adminSessionService;
    private final AuditLogService auditLogService;

    public AuditLogController(AdminSessionService adminSessionService, AuditLogService auditLogService) {
        this.adminSessionService = adminSessionService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AuditPage>> search(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "trace_id", required = false) String traceId,
        @RequestParam(value = "operation_module", required = false) String operationModule,
        @RequestParam(value = "operation_type", required = false) String operationType,
        @RequestParam(value = "target_type", required = false) String targetType,
        @RequestParam(value = "target_id", required = false) Long targetId,
        @RequestParam(value = "order_id", required = false) Long orderId,
        @RequestParam(value = "operator_user_id", required = false) Long operatorUserId,
        @RequestParam(value = "page_no", defaultValue = "1") int pageNo,
        @RequestParam(value = "page_size", defaultValue = "20") int pageSize
    ) {
        adminSessionService.requirePermission(authorization, "system:audit:read");
        AuditQuery query = new AuditQuery(
            traceId,
            operationModule,
            operationType,
            targetType,
            targetId,
            orderId,
            operatorUserId,
            Math.max(pageNo, 1),
            Math.min(Math.max(pageSize, 1), 100));
        return ResponseEntity.ok(ApiResponse.ok(auditLogService.search(query), TraceIds.currentOrCreate()));
    }
}
