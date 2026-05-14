package com.wecombft.interfaces.dto.iam;

public record WecomLoginRequest(String authCode, String redirectUri) {
}
