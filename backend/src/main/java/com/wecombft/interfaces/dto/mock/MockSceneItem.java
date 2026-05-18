package com.wecombft.interfaces.dto.mock;

public record MockSceneItem(
    String sceneCode,
    String capability,
    String displayName,
    String description,
    String defaultPayload,
    boolean enabled
) {
}
