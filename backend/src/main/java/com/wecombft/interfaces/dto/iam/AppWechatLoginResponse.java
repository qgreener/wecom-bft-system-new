package com.wecombft.interfaces.dto.iam;

public record AppWechatLoginResponse(
    String userNo,
    String studentNo,
    String accessToken,
    boolean mobileBound
) {
}
