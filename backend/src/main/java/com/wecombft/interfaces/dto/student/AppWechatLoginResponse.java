package com.wecombft.interfaces.dto.student;

import java.time.OffsetDateTime;

public record AppWechatLoginResponse(
    String userNo,
    long studentId,
    String studentNo,
    String accessToken,
    boolean mobileBound,
    String mobile,
    int expiresIn,
    OffsetDateTime expireAt
) {
}
