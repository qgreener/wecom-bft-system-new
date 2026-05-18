package com.wecombft.infrastructure.integration.wecom;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.wecombft.infrastructure.config.WecomProperties;

@Component
@ConditionalOnProperty(prefix = "integration", name = "wecom-auth-mode", havingValue = "real")
public class RealWecomAuthAdapter implements WecomAuthAdapter {

    private static final String OAUTH_AUTHORIZE = "https://open.weixin.qq.com/connect/oauth2/authorize";
    private static final String QYAPI_BASE = "https://qyapi.weixin.qq.com";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 7000L;

    private final WecomProperties properties;
    private final RestClient restClient;

    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>(null);

    public RealWecomAuthAdapter(WecomProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(QYAPI_BASE).build();
    }

    @Override
    public WecomOAuthUrl buildOAuthUrl(String state, String redirectUri) {
        String url = UriComponentsBuilder.fromUriString(OAUTH_AUTHORIZE)
            .queryParam("appid", properties.corpId())
            .queryParam("redirect_uri", URLEncoder.encode(redirectUri, StandardCharsets.UTF_8))
            .queryParam("response_type", "code")
            .queryParam("scope", "snsapi_privateinfo")
            .queryParam("agentid", properties.agentId())
            .queryParam("state", state)
            .build(true)
            .toUriString() + "#wechat_redirect";
        return new WecomOAuthUrl(url);
    }

    @Override
    public WecomUserInfo exchangeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new WecomAuthAdapterException("WECOM_CODE_EMPTY", "OAuth code 不能为空");
        }
        String accessToken = obtainAccessToken();
        Map<String, Object> userInfo = restClient.get()
            .uri(uri -> uri.path("/cgi-bin/auth/getuserinfo")
                .queryParam("access_token", accessToken)
                .queryParam("code", code)
                .build())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        ensureOk(userInfo, "WECOM_GET_USERINFO_FAILED", "调用 auth/getuserinfo 失败");
        String userId = (String) userInfo.get("userid");
        String openId = (String) userInfo.get("openid");
        if (userId == null || userId.isBlank()) {
            throw new WecomAuthAdapterException("WECOM_USERID_MISSING",
                "返回缺少 userid，可能是 OAuth code 已失效或 scope 错误");
        }
        Map<String, Object> detail = fetchUserDetail(accessToken, userId);
        String displayName = detail == null ? null : (String) detail.get("name");
        String mobile = detail == null ? null : (String) detail.get("mobile");
        return new WecomUserInfo(userId, openId, displayName, mobile);
    }

    private String obtainAccessToken() {
        CachedToken current = cachedToken.get();
        long now = Instant.now().getEpochSecond();
        if (current != null && current.expireAt > now + 30L) {
            return current.token;
        }
        Map<String, Object> response = restClient.get()
            .uri(uri -> uri.path("/cgi-bin/gettoken")
                .queryParam("corpid", properties.corpId())
                .queryParam("corpsecret", properties.agentSecret())
                .build())
            .retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        ensureOk(response, "WECOM_GETTOKEN_FAILED", "调用 gettoken 失败");
        String token = (String) response.get("access_token");
        if (token == null || token.isBlank()) {
            throw new WecomAuthAdapterException("WECOM_TOKEN_MISSING", "gettoken 返回缺少 access_token");
        }
        cachedToken.set(new CachedToken(token, now + ACCESS_TOKEN_TTL_SECONDS));
        return token;
    }

    private Map<String, Object> fetchUserDetail(String accessToken, String userId) {
        try {
            Map<String, Object> body = Map.of("userid", userId);
            return restClient.post()
                .uri(uri -> uri.path("/cgi-bin/auth/getuserdetail")
                    .queryParam("access_token", accessToken)
                    .build())
                .body(body)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private void ensureOk(Map<String, Object> response, String errorCode, String message) {
        if (response == null) {
            throw new WecomAuthAdapterException(errorCode, message + "（响应为空）");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
            throw new WecomAuthAdapterException(errorCode,
                message + "（errcode=" + errcode + ", errmsg=" + response.get("errmsg") + "）");
        }
    }

    private record CachedToken(String token, long expireAt) {
    }
}
