package com.wecombft.infrastructure.integration.wecom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    /**
     * 控件 ID 缓存。优先使用 application.yml 显式配置；缺省时调用 gettemplatedetail 自动识别。
     * 第一次成功识别后缓存到内存，后续不再请求企微。
     */
    private final AtomicReference<TemplateControlIds> resolvedControlIds = new AtomicReference<>(null);

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
            TemplateControlIds controlIds = resolveControlIds(accessToken);
            if (controlIds == null) {
                return WecomApprovalResult.failure("WECOM_APPROVAL_TEMPLATE_CONTROLS_NOT_RESOLVED");
            }
            Map<String, Object> body = buildApplyBody(command, controlIds);
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

    /**
     * 解析模板里"标题"和"描述"两个控件的 ID。
     * 优先用 application 配置；都未配时调企微 /cgi-bin/oa/gettemplatedetail 拉一次模板，
     * 按控件 control 属性识别第一个 Text 作标题、第一个 Textarea 作描述。
     */
    private TemplateControlIds resolveControlIds(String accessToken) {
        TemplateControlIds cached = resolvedControlIds.get();
        if (cached != null) {
            return cached;
        }
        String configuredTitle = approvalProperties.titleControlId();
        String configuredDesc = approvalProperties.descControlId();
        if (configuredTitle != null && !configuredTitle.isBlank()
            && configuredDesc != null && !configuredDesc.isBlank()) {
            TemplateControlIds resolved = new TemplateControlIds(configuredTitle, configuredDesc);
            resolvedControlIds.set(resolved);
            return resolved;
        }
        TemplateControlIds fetched = fetchControlIdsFromTemplate(accessToken);
        if (fetched != null) {
            resolvedControlIds.set(fetched);
        }
        return fetched;
    }

    @SuppressWarnings("unchecked")
    private TemplateControlIds fetchControlIdsFromTemplate(String accessToken) {
        try {
            Map<String, Object> response = restClient.post()
                .uri(uri -> uri.path("/cgi-bin/oa/gettemplatedetail")
                    .queryParam("access_token", accessToken)
                    .build())
                .body(Map.of("template_id", approvalProperties.templateId()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (response == null) {
                log.warn("WeCom gettemplatedetail returned null");
                return null;
            }
            Object errcode = response.get("errcode");
            if (errcode != null && !Integer.valueOf(0).equals(errcode)) {
                log.warn("WeCom gettemplatedetail non-zero errcode: errcode={} errmsg={}",
                    errcode, response.get("errmsg"));
                return null;
            }
            Map<String, Object> templateContent = (Map<String, Object>) response.get("template_content");
            if (templateContent == null) return null;
            List<Map<String, Object>> controls = (List<Map<String, Object>>) templateContent.get("controls");
            if (controls == null) return null;

            String titleId = null;
            String descId = null;
            for (Map<String, Object> control : controls) {
                Map<String, Object> property = (Map<String, Object>) control.get("property");
                if (property == null) continue;
                String controlType = (String) property.get("control");
                String controlId = (String) property.get("id");
                if (controlId == null) continue;
                if (titleId == null && "Text".equalsIgnoreCase(controlType)) {
                    titleId = controlId;
                } else if (descId == null && "Textarea".equalsIgnoreCase(controlType)) {
                    descId = controlId;
                }
            }
            if (titleId == null || descId == null) {
                log.warn("WeCom approval template missing required Text/Textarea controls: titleId={} descId={}",
                    titleId, descId);
                return null;
            }
            log.info("WeCom approval template controls resolved: titleId={} descId={}", titleId, descId);
            return new TemplateControlIds(titleId, descId);
        } catch (RuntimeException e) {
            log.warn("WeCom gettemplatedetail failed: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildApplyBody(WecomApprovalCommand command, TemplateControlIds controlIds) {
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(buildTextControl(controlIds.titleId(), command.title()));
        contents.add(buildTextareaControl(controlIds.descId(), command.description()));

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

    private Map<String, Object> buildTextControl(String controlId, String text) {
        return Map.of(
            "control", "Text",
            "id", controlId,
            "value", Map.of("text", text == null ? "" : text));
    }

    private Map<String, Object> buildTextareaControl(String controlId, String text) {
        return Map.of(
            "control", "Textarea",
            "id", controlId,
            "value", Map.of("text", text == null ? "" : text));
    }

    private record TemplateControlIds(String titleId, String descId) {
    }
}
