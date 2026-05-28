package com.wecombft.infrastructure.integration.wecom;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 缓存企业微信 JS-SDK 用到的两类 ticket：
 *
 *   1. "corp" ticket：/cgi-bin/get_jsapi_ticket?access_token=...
 *      用于 wx.config（普通 JS-SDK 能力）。
 *
 *   2. "agent_config" ticket：/cgi-bin/ticket/get?access_token=...&type=agent_config
 *      用于 wx.agentConfig（要拿外部联系人/客户群信息时必须用这个）。
 *
 * 两个 ticket 各自缓存 7000 秒（实际 TTL 7200 秒，留 200 秒余量），避免每次签名都调企微。
 */
@Component
public class WecomJsTicketManager {

    private static final Logger log = LoggerFactory.getLogger(WecomJsTicketManager.class);
    private static final String QYAPI_BASE = "https://qyapi.weixin.qq.com";
    private static final long TICKET_TTL_SECONDS = 7000L;

    private final WecomAccessTokenManager tokenManager;
    private final RestClient restClient;
    private final AtomicReference<CachedTicket> corpTicket = new AtomicReference<>(null);
    private final AtomicReference<CachedTicket> agentConfigTicket = new AtomicReference<>(null);

    public WecomJsTicketManager(WecomAccessTokenManager tokenManager, RestClient.Builder builder) {
        this.tokenManager = tokenManager;
        this.restClient = builder.baseUrl(QYAPI_BASE).build();
    }

    public String corpTicket() {
        return cachedOrFetch(corpTicket, () -> {
            String accessToken = tokenManager.obtainAccessToken();
            Map<String, Object> response = restClient.get()
                .uri(uri -> uri.path("/cgi-bin/get_jsapi_ticket")
                    .queryParam("access_token", accessToken)
                    .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseTicket(response, "get_jsapi_ticket");
        });
    }

    public String agentConfigTicket() {
        return cachedOrFetch(agentConfigTicket, () -> {
            String accessToken = tokenManager.obtainAccessToken();
            Map<String, Object> response = restClient.get()
                .uri(uri -> uri.path("/cgi-bin/ticket/get")
                    .queryParam("access_token", accessToken)
                    .queryParam("type", "agent_config")
                    .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return parseTicket(response, "ticket/get?type=agent_config");
        });
    }

    private String cachedOrFetch(AtomicReference<CachedTicket> ref, TicketLoader loader) {
        CachedTicket current = ref.get();
        long now = Instant.now().getEpochSecond();
        if (current != null && current.expireAt > now + 30L) {
            return current.ticket;
        }
        String ticket = loader.load();
        ref.set(new CachedTicket(ticket, now + TICKET_TTL_SECONDS));
        return ticket;
    }

    private String parseTicket(Map<String, Object> response, String endpoint) {
        if (response == null) {
            throw new IllegalStateException("WECOM " + endpoint + " response is null");
        }
        Object errcode = response.get("errcode");
        if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
            throw new IllegalStateException(
                "WECOM " + endpoint + " failed errcode=" + errcode + " errmsg=" + response.get("errmsg"));
        }
        String ticket = (String) response.get("ticket");
        if (ticket == null || ticket.isBlank()) {
            throw new IllegalStateException("WECOM " + endpoint + " returned empty ticket");
        }
        log.debug("WECOM ticket refreshed via {}", endpoint);
        return ticket;
    }

    @FunctionalInterface
    private interface TicketLoader {
        String load();
    }

    private record CachedTicket(String ticket, long expireAt) {
    }
}
