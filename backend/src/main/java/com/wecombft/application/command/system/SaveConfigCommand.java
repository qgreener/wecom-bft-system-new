package com.wecombft.application.command.system;

import java.util.List;

public record SaveConfigCommand(
    String configGroup,
    List<SaveConfigItem> configItems,
    String changeReason
) {
}
