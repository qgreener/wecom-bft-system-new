package com.wecombft.infrastructure.integration.wecom;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration", name = "wecom-auth-mode", havingValue = "mock", matchIfMissing = true)
public class MockWecomAuthAdapter implements WecomAuthAdapter {

    private static final String MOCK_PREFIX = "mock:";

    @Override
    public WecomOAuthUrl buildOAuthUrl(String state, String redirectUri) {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        return new WecomOAuthUrl("/admin/#/wecom-mock-login?state=" + state + "&redirect_uri=" + encodedRedirect);
    }

    @Override
    public WecomUserInfo exchangeCode(String code) {
        if (code == null || !code.startsWith(MOCK_PREFIX) || code.length() <= MOCK_PREFIX.length()) {
            throw new WecomAuthAdapterException("WECOM_MOCK_CODE_INVALID",
                "Mock 模式仅支持 mock:<user_no> 格式的 code");
        }
        String userNo = code.substring(MOCK_PREFIX.length()).trim();
        return new WecomUserInfo(
            "wecom_" + userNo,
            null,
            "Mock " + userNo,
            null);
    }
}
