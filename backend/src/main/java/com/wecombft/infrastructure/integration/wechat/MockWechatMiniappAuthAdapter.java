package com.wecombft.infrastructure.integration.wechat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration", name = "wechat-miniapp-auth-mode", havingValue = "mock", matchIfMissing = true)
public class MockWechatMiniappAuthAdapter implements WechatMiniappAuthAdapter {

    private static final String MOCK_PREFIX = "mock:";

    @Override
    public WechatMiniappSession code2Session(String code) {
        if (code == null || code.isBlank()) {
            throw new WechatMiniappAuthException("WX_CODE_EMPTY", "wx_code 不能为空");
        }
        String trimmed = code.trim();
        String identity = trimmed.startsWith(MOCK_PREFIX)
            ? trimmed.substring(MOCK_PREFIX.length())
            : trimmed;
        if (identity.isBlank()) {
            throw new WechatMiniappAuthException("WX_CODE_INVALID",
                "Mock 模式 wx_code 解析后为空");
        }
        return new WechatMiniappSession("mock_openid_" + identity, null, "mock_session_" + identity);
    }
}
