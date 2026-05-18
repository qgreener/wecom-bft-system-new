package com.wecombft.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.wecombft.infrastructure.integration.wecom.MockWecomAuthAdapter;
import com.wecombft.infrastructure.integration.wecom.WecomAuthAdapter;
import com.wecombft.infrastructure.integration.wecom.WecomAuthAdapterException;
import com.wecombft.infrastructure.integration.wecom.WecomUserInfo;
import com.wecombft.infrastructure.integration.wechat.MockWechatMiniappAuthAdapter;
import com.wecombft.infrastructure.integration.wechat.WechatMiniappAuthAdapter;
import com.wecombft.infrastructure.integration.wechat.WechatMiniappAuthException;
import com.wecombft.infrastructure.integration.wechat.WechatMiniappSession;

@SpringBootTest
@ActiveProfiles("test")
class AuthAdaptersWiringTest {

    @Autowired
    private WecomAuthAdapter wecomAuthAdapter;

    @Autowired
    private WechatMiniappAuthAdapter wechatMiniappAuthAdapter;

    @Test
    void should_load_mock_wecom_auth_adapter_in_test_profile() {
        assertThat(wecomAuthAdapter).isInstanceOf(MockWecomAuthAdapter.class);
    }

    @Test
    void should_load_mock_wechat_miniapp_auth_adapter_in_test_profile() {
        assertThat(wechatMiniappAuthAdapter).isInstanceOf(MockWechatMiniappAuthAdapter.class);
    }

    @Test
    void should_exchange_mock_wecom_code_to_synthetic_user() {
        WecomUserInfo info = wecomAuthAdapter.exchangeCode("mock:DEMO_ADMIN");
        assertThat(info.wecomUserId()).isEqualTo("wecom_DEMO_ADMIN");
        assertThat(info.displayName()).isEqualTo("Mock DEMO_ADMIN");
    }

    @Test
    void should_reject_non_mock_code_in_wecom_adapter() {
        assertThatThrownBy(() -> wecomAuthAdapter.exchangeCode("invalid"))
            .isInstanceOf(WecomAuthAdapterException.class);
    }

    @Test
    void should_build_wecom_oauth_url_with_state_and_redirect() {
        String url = wecomAuthAdapter.buildOAuthUrl("state123", "http://localhost/callback").url();
        assertThat(url).contains("state=state123");
    }

    @Test
    void should_translate_mock_prefixed_code_to_namespaced_openid() {
        WechatMiniappSession session = wechatMiniappAuthAdapter.code2Session("mock:DEMO_APP_STUDENT");
        assertThat(session.openid()).isEqualTo("mock_openid_DEMO_APP_STUDENT");
    }

    @Test
    void should_namespace_arbitrary_codes_under_mock_openid_prefix() {
        WechatMiniappSession session = wechatMiniappAuthAdapter.code2Session("real_wx_code_abc");
        assertThat(session.openid()).isEqualTo("mock_openid_real_wx_code_abc");
    }

    @Test
    void should_reject_empty_wechat_code() {
        assertThatThrownBy(() -> wechatMiniappAuthAdapter.code2Session(""))
            .isInstanceOf(WechatMiniappAuthException.class);
    }
}
