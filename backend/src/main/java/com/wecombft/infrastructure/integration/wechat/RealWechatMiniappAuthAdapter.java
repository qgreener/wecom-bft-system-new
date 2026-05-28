package com.wecombft.infrastructure.integration.wechat;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecombft.infrastructure.config.WechatMiniappProperties;

@Component
@ConditionalOnProperty(prefix = "integration", name = "wechat-miniapp-auth-mode", havingValue = "real")
public class RealWechatMiniappAuthAdapter implements WechatMiniappAuthAdapter {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 7000L;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final WechatMiniappProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
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
        String body = callWeChat(() -> restClient.get()
            .uri(uri -> uri.scheme("https").host("api.weixin.qq.com").path("/sns/jscode2session")
                .queryParam("appid", properties.appId())
                .queryParam("secret", properties.appSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build())
            .retrieve()
            .body(String.class), "code2session");
        Map<String, Object> response = parseJson(body, "code2session");
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
        Map<String, Object> reqBody = Map.of("code", phoneCode);
        String body = callWeChat(() -> restClient.post()
            .uri(uri -> uri.scheme("https").host("api.weixin.qq.com")
                .path("/wxa/business/getuserphonenumber")
                .queryParam("access_token", accessToken)
                .build())
            .body(reqBody)
            .retrieve()
            .body(String.class), "getuserphonenumber");
        Map<String, Object> response = parseJson(body, "getuserphonenumber");
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
        String body = callWeChat(() -> restClient.get()
            .uri(uri -> uri.scheme("https").host("api.weixin.qq.com").path("/cgi-bin/token")
                .queryParam("grant_type", "client_credential")
                .queryParam("appid", properties.appId())
                .queryParam("secret", properties.appSecret())
                .build())
            .retrieve()
            .body(String.class), "cgi-bin/token");
        Map<String, Object> response = parseJson(body, "cgi-bin/token");
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

    /** 包一层 RestClient 调用，统一把 HTTP 4xx/5xx 转成业务异常，避免直接抛 500 */
    private String callWeChat(java.util.function.Supplier<String> action, String api) {
        try {
            return action.get();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new WechatMiniappAuthException("WX_HTTP_" + e.getStatusCode().value(),
                api + " 调用失败 HTTP " + e.getStatusCode().value() + "（小程序后台可能未开通该接口或 IP 未加白）");
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new WechatMiniappAuthException("WX_NETWORK_FAILED",
                api + " 网络异常: " + e.getMessage());
        }
    }

    private Map<String, Object> parseJson(String body, String api) {
        if (body == null || body.isBlank()) {
            throw new WechatMiniappAuthException("WX_RESPONSE_EMPTY", api + " 返回为空");
        }
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (Exception e) {
            throw new WechatMiniappAuthException("WX_RESPONSE_PARSE_FAILED",
                api + " 响应解析失败: " + body, e);
        }
    }

    private record CachedToken(String token, long expireAt) {
    }
}
