package com.wecombft.interfaces.callback;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.wecombft.application.iam.ApprovalApplicationService;
import com.wecombft.infrastructure.config.WecomCallbackProperties;
import com.wecombft.infrastructure.config.WecomProperties;
import com.wecombft.infrastructure.integration.wecom.crypto.WXBizMsgCrypt;

/**
 * 企业微信「接收消息」回调端点。
 *
 * - GET：URL 验证（企微后台「接收消息服务器配置」点保存时触发）
 * - POST：审批结果 sys_approval_change 事件 → 调 {@link ApprovalApplicationService#applyWecomResult}
 *
 * 路径不在 AdminSecurityInterceptor 拦截范围（仅覆盖 /api/admin/**、/api/collab/**、
 * /api/wecom/sidebar/**），所以无需放行配置；签名校验由 WXBizMsgCrypt 自身完成。
 */
@RestController
public class WecomCallbackController {

    private static final Logger log = LoggerFactory.getLogger(WecomCallbackController.class);

    private final WecomCallbackProperties callbackProperties;
    private final WecomProperties wecomProperties;
    private final ApprovalApplicationService approvalApplicationService;

    public WecomCallbackController(
        WecomCallbackProperties callbackProperties,
        WecomProperties wecomProperties,
        ApprovalApplicationService approvalApplicationService
    ) {
        this.callbackProperties = callbackProperties;
        this.wecomProperties = wecomProperties;
        this.approvalApplicationService = approvalApplicationService;
    }

    @GetMapping(value = "/api/callbacks/wecom/receive", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyUrl(
        @RequestParam("msg_signature") String msgSignature,
        @RequestParam("timestamp") String timestamp,
        @RequestParam("nonce") String nonce,
        @RequestParam("echostr") String echoStr
    ) {
        ensureConfigured();
        try {
            String plain = WXBizMsgCrypt.verifyUrl(
                callbackProperties.token(),
                callbackProperties.aesKey(),
                wecomProperties.corpId(),
                msgSignature, timestamp, nonce, echoStr);
            return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(plain);
        } catch (WXBizMsgCrypt.WXBizMsgCryptException e) {
            log.warn("WeCom callback URL verification failed: {} {}", e.errorCode(), e.getMessage());
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body(e.errorCode());
        }
    }

    @PostMapping(value = "/api/callbacks/wecom/receive", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receive(
        @RequestParam("msg_signature") String msgSignature,
        @RequestParam("timestamp") String timestamp,
        @RequestParam("nonce") String nonce,
        @RequestBody String body
    ) {
        ensureConfigured();
        try {
            String encrypt = extractEncryptElement(body);
            if (encrypt == null) {
                return ResponseEntity.status(400).contentType(MediaType.TEXT_PLAIN).body("MISSING_ENCRYPT");
            }
            String plain = WXBizMsgCrypt.decryptMsg(
                callbackProperties.token(),
                callbackProperties.aesKey(),
                wecomProperties.corpId(),
                msgSignature, timestamp, nonce, encrypt);
            handleEvent(plain);
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("success");
        } catch (WXBizMsgCrypt.WXBizMsgCryptException e) {
            log.warn("WeCom callback decrypt failed: {} {}", e.errorCode(), e.getMessage());
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body(e.errorCode());
        } catch (RuntimeException e) {
            log.warn("WeCom callback handling failed: {}", e.getMessage(), e);
            return ResponseEntity.status(200).contentType(MediaType.TEXT_PLAIN).body("success");
        }
    }

    private void ensureConfigured() {
        if (callbackProperties.token() == null || callbackProperties.token().isBlank()
            || callbackProperties.aesKey() == null || callbackProperties.aesKey().isBlank()) {
            throw new WXBizMsgCrypt.WXBizMsgCryptException("CALLBACK_NOT_CONFIGURED",
                "WECOM_CALLBACK_TOKEN / WECOM_CALLBACK_AES_KEY 未配置");
        }
    }

    private String extractEncryptElement(String xml) {
        Document doc = parseXml(xml);
        return textOf(doc, "Encrypt");
    }

    private void handleEvent(String plainXml) {
        Document doc = parseXml(plainXml);
        String msgType = textOf(doc, "MsgType");
        String event = textOf(doc, "Event");
        if ("event".equalsIgnoreCase(msgType) && "sys_approval_change".equalsIgnoreCase(event)) {
            handleApprovalChange(doc);
            return;
        }
        log.info("WeCom callback ignored event: msgType={} event={}", msgType, event);
    }

    private void handleApprovalChange(Document doc) {
        String spNo = textOf(doc, "SpNo");
        String spStatus = textOf(doc, "SpStatus");
        String comment = textOf(doc, "Speech");
        if (spNo == null || spNo.isBlank()) {
            log.warn("sys_approval_change missing SpNo");
            return;
        }
        approvalApplicationService.applyWecomResult(spNo, spStatus, comment);
    }

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                return builder.parse(in);
            }
        } catch (Exception e) {
            throw new RuntimeException("XML_PARSE_FAILED: " + e.getMessage(), e);
        }
    }

    private String textOf(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        Element element = (Element) nodes.item(0);
        String text = element.getTextContent();
        return text == null ? null : text.trim();
    }
}
