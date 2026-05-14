package com.wecombft.application.system;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.persistence.system.SystemConfigRecord;
import com.wecombft.infrastructure.persistence.system.SystemConfigRepository;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.security.AdminSessionService;
import com.wecombft.shared.web.ApiException;

import com.wecombft.application.command.system.SaveConfigCommand;
import com.wecombft.application.command.system.SaveConfigItem;
import com.wecombft.interfaces.dto.system.ConfigGroupResponse;
import com.wecombft.interfaces.dto.system.ConfigItemResponse;
import com.wecombft.interfaces.dto.system.SaveConfigResponse;
@Service
public class SystemConfigService {

    private static final String SENSITIVE_STORED_PLACEHOLDER = "<SENSITIVE_SET>";
    private static final String MASKED_VALUE = "********";

    private final AdminSessionService adminSessionService;
    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogService auditLogService;

    public SystemConfigService(
        AdminSessionService adminSessionService,
        SystemConfigRepository systemConfigRepository,
        AuditLogService auditLogService
    ) {
        this.adminSessionService = adminSessionService;
        this.systemConfigRepository = systemConfigRepository;
        this.auditLogService = auditLogService;
    }

    public ConfigGroupResponse query(String authorizationHeader, String configGroup) {
        AdminPrincipal principal = adminSessionService.require(authorizationHeader);
        ensureReadAllowed(principal, configGroup);
        List<SystemConfigRecord> records = systemConfigRepository.findByGroup(normalizeGroup(configGroup));
        return toGroupResponse(normalizeGroup(configGroup), records);
    }

    @Transactional
    public SaveConfigResponse save(String authorizationHeader, SaveConfigCommand command) {
        AdminPrincipal principal = adminSessionService.requirePermission(authorizationHeader, "system:config:write");
        String group = normalizeGroup(command.configGroup());
        if (command.configItems() == null || command.configItems().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "配置项不能为空");
        }

        List<String> updatedKeys = new ArrayList<>();
        Long targetId = null;
        long version = 0;
        for (SaveConfigItem item : command.configItems()) {
            SystemConfigRecord current = systemConfigRepository.findByGroupAndKey(group, item.configKey())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "配置项不存在"));
            if (!current.editable()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "配置项不可编辑");
            }

            String storedValue = current.sensitive() ? SENSITIVE_STORED_PLACEHOLDER : normalizePlainValue(item.configValue());
            String maskedValue = current.sensitive() ? MASKED_VALUE : storedValue;
            systemConfigRepository.updateValue(current.id(), storedValue, maskedValue, principal.userId());
            updatedKeys.add(current.configKey());
            targetId = targetId == null ? current.id() : targetId;
            version = Math.max(version, current.version() + 1);
        }

        long auditLogId = auditLogService.writeSuccess(
            principal,
            "SYSTEM_CONFIG",
            "CONFIG_SAVE",
            "SYS_CONFIG",
            targetId,
            group,
            null,
            "{\"config_group\":\"" + group + "\",\"updated_keys\":\"" + String.join(",", updatedKeys) + "\"}");

        return new SaveConfigResponse(group, updatedKeys, version, LocalDateTime.now(), auditLogId);
    }

    private void ensureReadAllowed(AdminPrincipal principal, String configGroup) {
        String group = normalizeGroup(configGroup);
        List<String> permissions = principal.permissionView().permissionCodes();
        if (permissions.contains("system:config:read")) {
            return;
        }
        if ("LOGISTICS".equals(group) && permissions.contains("system:logistics-config:read")) {
            return;
        }
        if ("INVOICE".equals(group) && permissions.contains("system:tax-config:read")) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限访问该配置");
    }

    private ConfigGroupResponse toGroupResponse(String configGroup, List<SystemConfigRecord> records) {
        return new ConfigGroupResponse(
            configGroup,
            records.stream()
                .map(record -> new ConfigItemResponse(
                    record.configKey(),
                    record.displayName(),
                    record.sensitive() ? MASKED_VALUE : record.maskedValue(),
                    record.editable(),
                    record.updatedAt(),
                    record.updatedBy(),
                    record.version()))
                .toList());
    }

    private String normalizeGroup(String configGroup) {
        if (configGroup == null || configGroup.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "配置分组不能为空");
        }
        return configGroup.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePlainValue(String value) {
        return value == null ? "" : value.trim();
    }





}
