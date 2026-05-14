package com.wecombft.interfaces.dto.system;

import com.wecombft.application.command.system.SaveConfigItem;

public record SaveConfigItemRequest(
    String configKey,
    String configValue
) {
    public SaveConfigItem toCommand() {
        return new SaveConfigItem(
            configKey,
            configValue);
    }
}
