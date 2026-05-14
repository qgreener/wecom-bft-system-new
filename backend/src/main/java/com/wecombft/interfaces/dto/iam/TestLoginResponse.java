package com.wecombft.interfaces.dto.iam;

import java.time.OffsetDateTime;

public record TestLoginResponse(
    String userNo,
    String accessToken,
    int expiresIn,
    String tokenType,
    OffsetDateTime expireAt
) {
}
