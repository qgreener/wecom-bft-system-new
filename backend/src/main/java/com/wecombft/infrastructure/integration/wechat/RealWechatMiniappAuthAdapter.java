package com.wecombft.infrastructure.integration.wechat;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.wecombft.infrastructure.config.WechatMiniappProperties;

@Component
@ConditionalOnProperty(prefix = "integration", name = "wechat-miniapp-auth-mode", havingValue = "real")
public class RealWechatMiniappAuthAdapter implements WechatMiniappAuthAdapter {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 7000L;

    private final WechatMiniappProperties properties;
    private final RestClient restClient;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>(null);

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

    @Override
    @SuppressWarnings("unchecked")
    public String getPhoneNumber(String phoneCode) {
        if (phoneCode == null || phoneCode.isBlank()) {
            throw new WechatMiniappAuthException("WX_PHONE_CODE_EMPTY", "phone_code 不能为空");
        }
        String accessToken = obtainAccessToken();
        Map<String, Object> body = Map.of("code", phoneCode);
        Map<String, Object> response = restClient.post()
            .uri(uri -> uri.scheme("https").host("api.weixin.qq.com")
                .path("/wxa/business/getuserphonenumber")
                .queryParam("access_token", accessToken)
                .build())
            .body(body)
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) {
            throw new WechatMiniappAuthException("WX_PHONE_EMPTY", "getuserphonenumber 返回为空");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
            throw new WechatMiniappAuthException("WX_PHONE_FAILED",
                "getuserphonenumber errcode=" + errcode + " errmsg=" + response.get("errmsg"));
        }
        Object phoneInfo = response.get("phone_info");
        if (!(phoneInfo instanceof Map)) {
            throw new WechatMiniappAuthException("WX_PHONE_MISSING", "返回缺少 phone_info");
        }
        Object purePhone = ((Map<String, Object>) phoneInfo).get("purePhoneNumber");
        Object phoneNumber = ((Map<String, Object>) phoneInfo).get("phoneNumber");
        String resolved = (String) (purePhone != null ? purePhone : phoneNumber);
        if (resolved == null || resolved.isBlank()) {
            throw new WechatMiniappAuthException("WX_PHONE_MISSING", "phone_info 中缺少手机号");
        }
        return resolved;
    }

    private String obtainAccessToken() {
        CachedToken current = cachedToken.get();
        long now = Instant.now().getEpochSecond();
        if (current != null && current.expireAt > now + 30L) {
            return current.token;
        }
        Map<String, Object> response = restClient.get()
            .uri(uri -> uri.scheme("https").host("api.weixin.qq.com").path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", properties.appId())
                .queryParam("secret", properties.appSecret())
                .build())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) {
            throw new WechatMiniappAuthException("WX_GETTOKEN_EMPTY", "cgi-bin/token 返回为空");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
            throw new WechatMiniappAuthException("WX_GETTOKEN_FAILED",
                "cgi-bin/token errcode=" + errcode + " errmsg=" + response.get("errmsg"));
        }
        String token = (String) response.get("access_token");
        if (token == null || token.isBlank()) {
            throw new WechatMiniappAuthException("WX_TOKEN_MISSING", "cgi-bin/token 缺少 access_token");
        }
        cachedToken.set(new CachedToken(token, now + ACCESS_TOKEN_TTL_SECONDS));
        return token;
    }

    private record CachedToken(String token, long expireAt) {
    }
}
