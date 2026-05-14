package com.wecombft.interfaces.dto.system;

import com.wecombft.application.command.system.SaveConfigCommand;
import java.util.List;

public record SaveConfigRequest(
    String configGroup,
    List<SaveConfigItemRequest> configItems,
    String changeReason
) {
    public SaveConfigCommand toCommand() {
        return new SaveConfigCommand(
            configGroup,
            configItems == null ? null : configItems.stream().map(SaveConfigItemRequest::toCommand).toList(),
            changeReason);
    }
}
