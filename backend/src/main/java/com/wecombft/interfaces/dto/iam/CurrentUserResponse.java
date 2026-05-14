package com.wecombft.interfaces.dto.iam;

import java.util.List;

public record CurrentUserResponse(
    long userId,
    String userNo,
    String displayName,
    List<RoleView> roles,
    List<String> permissionCodes,
    DataScopeView dataScope,
    List<MenuView> menus,
    List<FieldMaskView> fieldMasks
) {
}
