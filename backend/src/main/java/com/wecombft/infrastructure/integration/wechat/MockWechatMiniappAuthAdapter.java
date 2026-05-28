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

    @Override
    public String getPhoneNumber(String phoneCode) {
        if (phoneCode == null || phoneCode.isBlank()) {
            throw new WechatMiniappAuthException("WX_PHONE_CODE_EMPTY", "phone_code 不能为空");
        }
        String value = phoneCode.trim();
        if (value.startsWith(MOCK_PREFIX)) {
            value = value.substring(MOCK_PREFIX.length()).trim();
        }
        if (!value.matches("1[3-9]\\d{9}")) {
            throw new WechatMiniappAuthException("WX_PHONE_INVALID",
                "Mock 模式手机号格式非法，期望 mock:13xxxxxxxxx 形式");
        }
        return value;
    }
}
