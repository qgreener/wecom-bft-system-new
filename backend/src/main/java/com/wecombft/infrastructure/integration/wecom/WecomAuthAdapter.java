package com.wecombft.infrastructure.integration.wecom;

public interface WecomAuthAdapter {

    WecomOAuthUrl buildOAuthUrl(String state, String redirectUri);

    WecomUserInfo exchangeCode(String code);
}
