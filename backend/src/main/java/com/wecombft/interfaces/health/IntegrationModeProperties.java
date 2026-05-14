package com.wecombft.interfaces.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integration")
public record IntegrationModeProperties(
    String defaultMode,
    String paymentMode,
    String refundMode,
    String logisticsMode,
    String invoiceMode,
    String wecomMode,
    String messageMode
) {
}
