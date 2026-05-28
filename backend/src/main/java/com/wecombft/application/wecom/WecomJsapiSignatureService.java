package com.wecombft.application.wecom;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.config.WecomProperties;
import com.wecombft.infrastructure.integration.wecom.WecomJsTicketManager;
import com.wecombft.shared.web.ApiException;

/**
 * 给企微侧边栏 H5 出 JS-SDK 签名用：
 *   wx.config 走 corp ticket；
 *   wx.agentConfig 走 agent_config ticket。
 * 两者签名算法相同，都是按 key 字典序拼接后 SHA1。
 */
@Service
public class WecomJsapiSignatureService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final WecomProperties wecomProperties;
    private final WecomJsTicketManager ticketManager;

    public WecomJsapiSignatureService(WecomProperties wecomProperties, WecomJsTicketManager ticketManager) {
        this.wecomProperties = wecomProperties;
        this.ticketManager = ticketManager;
    }

    public JsapiSignaturePayload sign(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "url 不能为空");
        }
        String url = stripFragment(pageUrl.trim());
        long timestamp = System.currentTimeMillis() / 1000L;
        String nonce = randomNonce();

        try {
            String corpTicket = ticketManager.corpTicket();
            String agentTicket = ticketManager.agentConfigTicket();
            String wxConfigSignature = sha1(buildSignString(corpTicket, nonce, timestamp, url));
            String agentConfigSignature = sha1(buildSignString(agentTicket, nonce, timestamp, url));

            return new JsapiSignaturePayload(
                wecomProperties.corpId(),
                wecomProperties.agentId(),
                timestamp,
                nonce,
                url,
                wxConfigSignature,
                agentConfigSignature);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "WECOM_JSAPI_SIGN_FAILED",
                "JSAPI 签名失败：" + e.getMessage());
        }
    }

    private static String buildSignString(String ticket, String nonce, long timestamp, String url) {
        // 按字典序排序 4 个参数，key=value 用 & 拼接
        Map<String, String> params = new TreeMap<>();
        params.put("jsapi_ticket", ticket);
        params.put("noncestr", nonce);
        params.put("timestamp", Long.toString(timestamp));
        params.put("url", url);
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm unavailable", e);
        }
    }

    private static String stripFragment(String url) {
        int hash = url.indexOf('#');
        return hash < 0 ? url : url.substring(0, hash);
    }

    private static String randomNonce() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public record JsapiSignaturePayload(
        String corpId,
        String agentId,
        long timestamp,
        String nonceStr,
        String url,
        String wxConfigSignature,
        String agentConfigSignature
    ) {
    }
}
