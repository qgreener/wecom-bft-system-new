package com.wecombft.application.iam;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.shared.web.ApiException;

@Service
public class SecurityScopeSampleService {

    private final AdminSessionService adminSessionService;
    private final AuditLogService auditLogService;

    public SecurityScopeSampleService(
        AdminSessionService adminSessionService,
        AuditLogService auditLogService
    ) {
        this.adminSessionService = adminSessionService;
        this.auditLogService = auditLogService;
    }

    public ScopeSampleResponse getSample(String authorizationHeader, String sampleId) {
        AdminPrincipal principal = adminSessionService.require(authorizationHeader);
        adminSessionService.requireAnyPermission(principal, List.of("course:lesson:write", "course:spec:write"));

        ScopeSample sample = switch (sampleId) {
            case "teacher-owned-course" -> new ScopeSample(sampleId, true);
            case "other-teacher-course" -> new ScopeSample(sampleId, false);
            default -> throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "样例数据不存在");
        };

        if (!canAccess(principal, sample)) {
            auditLogService.writeFailure(
                principal,
                "SECURITY",
                "DATA_SCOPE_DENIED",
                "SCOPE_SAMPLE",
                null,
                sample.sampleId(),
                null,
                "数据范围不允许访问样例：" + sample.sampleId());
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限访问该数据范围");
        }

        return new ScopeSampleResponse(sample.sampleId(), "139****0000", "VISIBLE");
    }

    private boolean canAccess(AdminPrincipal principal, ScopeSample sample) {
        String scopeCode = principal.permissionView().dataScope().scopeCode();
        if ("ALL".equals(scopeCode) || "COURSE_ALL".equals(scopeCode)) {
            return true;
        }
        return sample.ownedByTeacher() && "OWN_COURSE".equals(scopeCode);
    }

    private record ScopeSample(String sampleId, boolean ownedByTeacher) {
    }

    public record ScopeSampleResponse(
        String sampleId,
        String studentMobile,
        String learningRecord
    ) {
    }
}
