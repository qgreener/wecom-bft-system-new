package com.wecombft.application.audit;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.persistence.audit.AuditLogRecord;
import com.wecombft.infrastructure.persistence.audit.AuditLogRepository;
import com.wecombft.infrastructure.persistence.audit.AuditLogRepository.AuditQuery;
import com.wecombft.infrastructure.persistence.audit.AuditLogRepository.AuditWriteCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.trace.TraceIds;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final IdGenerator idGenerator;

    public AuditLogService(AuditLogRepository auditLogRepository, IdGenerator idGenerator) {
        this.auditLogRepository = auditLogRepository;
        this.idGenerator = idGenerator;
    }

    public long writeSuccess(
        AdminPrincipal principal,
        String operationModule,
        String operationType,
        String targetType,
        Long targetId,
        String targetNo,
        Long orderId,
        String afterSnapshot
    ) {
        long auditId = idGenerator.nextId();
        auditLogRepository.insert(new AuditWriteCommand(
            auditId,
            TraceIds.currentOrCreate(),
            principal.userId(),
            principal.displayName(),
            operationModule,
            operationType,
            targetType,
            targetId,
            targetNo,
            orderId,
            null,
            sanitizeSnapshot(afterSnapshot),
            "SUCCESS",
            null,
            null,
            null,
            LocalDateTime.now()
        ));
        return auditId;
    }

    public long writeFailure(
        AdminPrincipal principal,
        String operationModule,
        String operationType,
        String targetType,
        Long targetId,
        String targetNo,
        Long orderId,
        String failureReason
    ) {
        long auditId = idGenerator.nextId();
        auditLogRepository.insert(new AuditWriteCommand(
            auditId,
            TraceIds.currentOrCreate(),
            principal == null ? null : principal.userId(),
            principal == null ? null : principal.displayName(),
            operationModule,
            operationType,
            targetType,
            targetId,
            targetNo,
            orderId,
            null,
            null,
            "FAILED",
            sanitizeSnapshot(failureReason),
            null,
            null,
            LocalDateTime.now()
        ));
        return auditId;
    }

    public AuditPage search(AuditQuery query) {
        List<AuditLogRecord> records = auditLogRepository.search(query);
        return new AuditPage(records, query.pageNo(), query.pageSize(), auditLogRepository.count(query));
    }

    private String sanitizeSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return snapshot;
        }
        return snapshot
            .replaceAll("(?i)(secret|token|password|api[_-]?v3[_-]?key)\"?\\s*:\\s*\"[^\"]*\"", "$1\":\"********\"")
            .replaceAll("1[3-9]\\d{9}", "139****0000");
    }

    public record AuditPage(List<AuditLogRecord> records, int pageNo, int pageSize, int total) {
    }
}
