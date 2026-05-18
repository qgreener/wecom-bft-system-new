package com.wecombft.infrastructure.integration.wecom;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RealWecomCardAdapter implements WecomCardAdapter {

    private static final Logger log = LoggerFactory.getLogger(RealWecomCardAdapter.class);
    private static final String QYAPI_BASE = "https://qyapi.weixin.qq.com";

    private final WecomAccessTokenManager tokenManager;
    private final RestClient restClient;

    public RealWecomCardAdapter(WecomAccessTokenManager tokenManager, RestClient.Builder builder) {
        this.tokenManager = tokenManager;
        this.restClient = builder.baseUrl(QYAPI_BASE).build();
    }

    @Override
    public WecomCardResult sendCard(WecomCardCommand command) {
        if (command == null || command.receiverWecomUserId() == null || command.receiverWecomUserId().isBlank()) {
            return WecomCardResult.failure("RECEIVER_WECOM_USER_ID_EMPTY");
        }
        try {
            String accessToken = tokenManager.obtainAccessToken();
            Map<String, Object> body = Map.of(
                "touser", command.receiverWecomUserId(),
                "agentid", Integer.parseInt(tokenManager.agentId()),
                "msgtype", "textcard",
                "textcard", buildTextcard(command));
            Map<String, Object> response = restClient.post()
                .uri(uri -> uri.path("/cgi-bin/message/send")
                    .queryParam("access_token", accessToken)
                    .build())
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) {
                return WecomCardResult.failure("WECOM_MESSAGE_SEND_NULL_RESPONSE");
            }
            Object errcode = response.get("errcode");
            if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
                String reason = "errcode=" + errcode + " errmsg=" + response.get("errmsg");
                log.warn("WeCom sendCard non-zero errcode: {}", reason);
                return WecomCardResult.failure(reason);
            }
            String msgId = response.get("msgid") == null ? null : String.valueOf(response.get("msgid"));
            return WecomCardResult.success(msgId);
        } catch (RuntimeException e) {
            log.warn("WeCom sendCard failed: {}", e.getMessage());
            return WecomCardResult.failure(e.getMessage());
        }
    }

    private Map<String, Object> buildTextcard(WecomCardCommand command) {
        String url = command.url() == null || command.url().isBlank()
            ? "https://finhub.tax/"
            : command.url();
        String btnText = command.btnText() == null || command.btnText().isBlank()
            ? "查看详情"
            : command.btnText();
        return Map.of(
            "title", command.title() == null ? "" : command.title(),
            "description", command.description() == null ? "" : command.description(),
            "url", url,
            "btntxt", btnText);
    }
}
