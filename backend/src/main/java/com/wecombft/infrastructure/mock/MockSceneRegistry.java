package com.wecombft.infrastructure.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class MockSceneRegistry {

    private final Map<String, MockSceneDefinition> scenes = new LinkedHashMap<>();

    public synchronized void register(MockSceneDefinition definition) {
        if (definition == null || definition.sceneCode() == null || definition.sceneCode().isBlank()) {
            throw new IllegalArgumentException("MockSceneDefinition.sceneCode 不能为空");
        }
        scenes.put(definition.sceneCode(), definition);
    }

    public Optional<MockSceneDefinition> findByCode(String sceneCode) {
        return Optional.ofNullable(scenes.get(sceneCode));
    }

    public List<MockSceneDefinition> findAll(String capability, boolean enabledOnly) {
        List<MockSceneDefinition> result = new ArrayList<>();
        for (MockSceneDefinition def : scenes.values()) {
            if (capability != null && !capability.isBlank() && !capability.equalsIgnoreCase(def.capability())) {
                continue;
            }
            if (enabledOnly && !def.enabled()) {
                continue;
            }
            result.add(def);
        }
        return result;
    }

    public int size() {
        return scenes.size();
    }
}
