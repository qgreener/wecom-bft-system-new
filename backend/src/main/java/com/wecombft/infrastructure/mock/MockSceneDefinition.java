package com.wecombft.infrastructure.mock;

import java.util.function.Function;

public record MockSceneDefinition(
    String sceneCode,
    String capability,
    String displayName,
    String description,
    String defaultPayload,
    boolean enabled,
    Function<MockTriggerContext, MockDispatchResult> dispatcher
) {
}
