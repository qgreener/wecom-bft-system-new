package com.wecombft.interfaces.health;

public record DependencyCheckPayload(
    String name,
    String status,
    long latencyMs,
    String message
) {
}
