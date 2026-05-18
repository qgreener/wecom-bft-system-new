package com.wecombft.application.iam;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.mock.MockDispatchResult;
import com.wecombft.infrastructure.mock.MockSceneDefinition;
import com.wecombft.infrastructure.mock.MockSceneRegistry;
import com.wecombft.infrastructure.mock.MockTriggerContext;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.interfaces.dto.mock.MockSceneItem;
import com.wecombft.interfaces.dto.mock.MockScenesResponse;
import com.wecombft.interfaces.dto.mock.MockTriggerRequest;
import com.wecombft.interfaces.dto.mock.MockTriggerResponse;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class MockSceneApplicationService {

    private final MockSceneRegistry registry;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public MockSceneApplicationService(
        MockSceneRegistry registry,
        IdGenerator idGenerator,
        AuditLogService auditLogService
    ) {
        this.registry = registry;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    public MockScenesResponse listScenes(AdminPrincipal principal, String capability, Boolean enabledOnly) {
        requireAdmin(principal);
        boolean filter = enabledOnly != null && enabledOnly;
        List<MockSceneItem> items = registry.findAll(capability, filter).stream()
            .map(def -> new MockSceneItem(
                def.sceneCode(),
                def.capability(),
                def.displayName(),
                def.description(),
                def.defaultPayload(),
                def.enabled()))
            .toList();
        return new MockScenesResponse(items);
    }

    public MockTriggerResponse triggerScene(
        AdminPrincipal principal,
        String sceneCode,
        String idempotencyKey,
        MockTriggerRequest request
    ) {
        requireAdmin(principal);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "Mock 触发缺少幂等键");
        }
        MockSceneDefinition def = registry.findByCode(sceneCode)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Mock 场景不存在: " + sceneCode));
        if (!def.enabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Mock 场景已禁用: " + sceneCode);
        }

        String mockEventNo = "MOCK" + idGenerator.nextId();
        MockTriggerContext context = new MockTriggerContext(
            def.sceneCode(),
            def.capability(),
            request == null ? null : request.targetNo(),
            request == null ? Map.of() : (request.payloadOverride() == null ? Map.of() : request.payloadOverride()),
            request == null ? null : request.triggerReason(),
            idempotencyKey,
            mockEventNo);

        MockDispatchResult dispatchResult;
        try {
            dispatchResult = def.dispatcher().apply(context);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            auditLogService.writeFailure(
                principal,
                "MOCK",
                "MOCK_TRIGGER_FAILED",
                "MOCK_SCENE",
                null,
                truncate(sceneCode, 60),
                null,
                "Mock 触发失败：" + e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_TRIGGER_FAILED",
                "Mock 场景触发失败: " + e.getMessage());
        }

        auditLogService.writeSuccess(
            principal,
            "MOCK",
            "MOCK_TRIGGER",
            "MOCK_SCENE",
            null,
            truncate(sceneCode, 60),
            null,
            "{\"scene_code\":\"" + sceneCode + "\",\"target_no\":\"" + sanitize(context.targetNo()) + "\"}");

        return new MockTriggerResponse(
            mockEventNo,
            def.sceneCode(),
            dispatchResult == null ? null : dispatchResult.callbackPath(),
            dispatchResult == null ? "PROCESSED" : dispatchResult.processingStatus(),
            dispatchResult == null ? null : dispatchResult.integrationCallbackEventNo(),
            dispatchResult == null ? null : dispatchResult.message());
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max - 3) + "..." : value;
    }
}
