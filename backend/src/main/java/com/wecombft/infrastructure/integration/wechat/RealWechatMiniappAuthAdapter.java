package com.wecombft.infrastructure.integration.wechat;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.wecombft.infrastructure.config.WechatMiniappProperties;

@Component
@ConditionalOnProperty(prefix = "integration", name = "wechat-miniapp-auth-mode", havingValue = "real")
public class RealWechatMiniappAuthAdapter implements WechatMiniappAuthAdapter {

    private final WechatMiniappProperties properties;
    private final RestClient restClient;

    public RealWechatMiniappAuthAdapter(WechatMiniappProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public WechatMiniappSession code2Session(String code) {
        if (code == null || code.isBlank()) {
            throw new WechatMiniappAuthException("WX_CODE_EMPTY", "wx_code 不能为空");
        }
        Map<String, Object> response = restClient.get()
            .uri(uri -> uri.scheme("https").host("api.weixin.qq.com").path("/sns/jscode2session")
                .queryParam("appid", properties.appId())
                .queryParam("secret", properties.appSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) {
            throw new WechatMiniappAuthException("WX_CODE2SESSION_EMPTY", "code2session 返回为空");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
            throw new WechatMiniappAuthException("WX_CODE2SESSION_FAILED",
                "code2session errcode=" + errcode + " errmsg=" + response.get("errmsg"));
        }
        String openid = (String) response.get("openid");
        if (openid == null || openid.isBlank()) {
            throw new WechatMiniappAuthException("WX_OPENID_MISSING", "code2session 返回缺少 openid");
        }
        String unionid = (String) response.get("unionid");
        String sessionKey = (String) response.get("session_key");
        return new WechatMiniappSession(openid, unionid, sessionKey);
    }
}
