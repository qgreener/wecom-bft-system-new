package com.wecombft.infrastructure.integration.wecom;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.wecombft.infrastructure.config.WecomProperties;

@Component
public class WecomAccessTokenManager {

    private static final String QYAPI_BASE = "https://qyapi.weixin.qq.com";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 1800L;

    private final WecomProperties properties;
    private final RestClient restClient;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>(null);

    public WecomAccessTokenManager(WecomProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(QYAPI_BASE).build();
    }

    public String obtainAccessToken() {
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
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        if (response == null) {
            throw new IllegalStateException("WECOM gettoken response is null");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
            throw new IllegalStateException(
                "WECOM gettoken failed errcode=" + errcode + " errmsg=" + response.get("errmsg"));
        }
        String token = (String) response.get("access_token");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("WECOM gettoken returned empty access_token");
        }
        cachedToken.set(new CachedToken(token, now + ACCESS_TOKEN_TTL_SECONDS));
        return token;
    }

    public String agentId() {
        return properties.agentId();
    }

    public String corpId() {
        return properties.corpId();
    }

    private record CachedToken(String token, long expireAt) {
    }
}
