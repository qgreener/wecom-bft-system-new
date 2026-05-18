package com.wecombft.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wecom.approval")
public record WecomApprovalProperties(
    String templateId,
    String titleControlId,
    String descControlId
) {
}
