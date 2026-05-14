package com.wecombft.interfaces.dto.system;

import java.util.List;

public record ConfigGroupResponse(String configGroup, List<ConfigItemResponse> configItems) {
}
