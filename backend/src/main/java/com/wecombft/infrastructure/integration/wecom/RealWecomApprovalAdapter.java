package com.wecombft.infrastructure.integration.wecom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.wecombft.infrastructure.config.WecomApprovalProperties;

@Component
public class RealWecomApprovalAdapter implements WecomApprovalAdapter {

    private static final Logger log = LoggerFactory.getLogger(RealWecomApprovalAdapter.class);
    private static final String QYAPI_BASE = "https://qyapi.weixin.qq.com";

    private final WecomAccessTokenManager tokenManager;
    private final WecomApprovalProperties approvalProperties;
    private final RestClient restClient;

    public RealWecomApprovalAdapter(
        WecomAccessTokenManager tokenManager,
        WecomApprovalProperties approvalProperties,
        RestClient.Builder builder
    ) {
        this.tokenManager = tokenManager;
        this.approvalProperties = approvalProperties;
        this.restClient = builder.baseUrl(QYAPI_BASE).build();
    }

    @Override
    public WecomApprovalResult createApproval(WecomApprovalCommand command) {
        if (command == null || command.creatorWecomUserId() == null || command.creatorWecomUserId().isBlank()) {
            return WecomApprovalResult.failure("CREATOR_WECOM_USER_ID_EMPTY");
        }
        if (approvalProperties.templateId() == null || approvalProperties.templateId().isBlank()) {
            return WecomApprovalResult.failure("WECOM_APPROVAL_TEMPLATE_NOT_CONFIGURED");
        }
        try {
            String accessToken = tokenManager.obtainAccessToken();
            Map<String, Object> body = buildApplyBody(command);
            Map<String, Object> response = restClient.post()
                .uri(uri -> uri.path("/cgi-bin/oa/applyevent")
                    .queryParam("access_token", accessToken)
                    .build())
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) {
                return WecomApprovalResult.failure("WECOM_APPLYEVENT_NULL_RESPONSE");
            }
            Object errcode = response.get("errcode");
            if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
                String reason = "errcode=" + errcode + " errmsg=" + response.get("errmsg");
                log.warn("WeCom createApproval non-zero errcode: {}", reason);
                return WecomApprovalResult.failure(reason);
            }
            String spNo = (String) response.get("sp_no");
            if (spNo == null || spNo.isBlank()) {
                return WecomApprovalResult.failure("WECOM_APPLYEVENT_MISSING_SP_NO");
            }
            return WecomApprovalResult.success(spNo);
        } catch (RuntimeException e) {
            log.warn("WeCom createApproval failed: {}", e.getMessage());
            return WecomApprovalResult.failure(e.getMessage());
        }
    }

    private Map<String, Object> buildApplyBody(WecomApprovalCommand command) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(buildTextControl(approvalProperties.titleControlId(), "标题", command.title()));
        contents.add(buildTextareaControl(approvalProperties.descControlId(), "描述", command.description()));

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("creator_userid", command.creatorWecomUserId());
        body.put("template_id", approvalProperties.templateId());
        if (command.approverWecomUserIds() != null && !command.approverWecomUserIds().isEmpty()) {
            body.put("use_template_approver", 0);
            body.put("approver", List.of(Map.of(
                "attr", 1,
                "userid", command.approverWecomUserIds())));
        } else {
            body.put("use_template_approver", 1);
        }
        body.put("apply_data", Map.of("contents", contents));
        body.put("summary_list", List.of(Map.of(
            "summary_info", List.of(Map.of(
                "text", command.title() == null ? "" : command.title(),
                "lang", "zh_CN")))));
        return body;
    }

    private Map<String, Object> buildTextControl(String controlId, String defaultLabel, String text) {
        return Map.of(
            "control", "Text",
            "id", controlId == null ? defaultLabel : controlId,
            "value", Map.of("text", text == null ? "" : text));
    }

    private Map<String, Object> buildTextareaControl(String controlId, String defaultLabel, String text) {
        return Map.of(
            "control", "Textarea",
            "id", controlId == null ? defaultLabel : controlId,
            "value", Map.of("text", text == null ? "" : text));
    }
}
