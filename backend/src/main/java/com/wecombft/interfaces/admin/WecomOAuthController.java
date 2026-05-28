package com.wecombft.interfaces.admin;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.AdminAuthApplicationService;
import com.wecombft.infrastructure.config.WecomProperties;
import com.wecombft.interfaces.dto.iam.TestLoginResponse;

@RestController
public class WecomOAuthController {

    private static final long STATE_TTL_SECONDS = 300L;

    private final AdminAuthApplicationService authApplicationService;
    private final WecomProperties wecomProperties;

    private final ConcurrentHashMap<String, StateEntry> stateStore = new ConcurrentHashMap<>();

    public WecomOAuthController(AdminAuthApplicationService authApplicationService, WecomProperties wecomProperties) {
        this.authApplicationService = authApplicationService;
        this.wecomProperties = wecomProperties;
    }

    @GetMapping("/api/admin/auth/wecom-oauth/start-redirect")
    public ResponseEntity<Void> startRedirect(
        @RequestParam(value = "from", required = false) String from
    ) {
        purgeExpiredStates();
        String state = UUID.randomUUID().toString().replace("-", "");
        stateStore.put(state, new StateEntry(Instant.now().getEpochSecond() + STATE_TTL_SECONDS, from));
        String redirectUri = resolveRedirectUri();
        String authorizeUrl = authApplicationService.buildWecomOAuthStartUrl(state, redirectUri);
        return redirect(URI.create(authorizeUrl));
    }

    /**
     * 给前端登录页用的：拿 corpId / agentId / redirect_uri / state，自己渲染企微 wwLogin 二维码。
     */
    @GetMapping("/api/admin/auth/wecom-oauth/qr-config")
    public org.springframework.http.ResponseEntity<com.wecombft.shared.web.ApiResponse<QrConfigResponse>> qrConfig() {
        purgeExpiredStates();
        String state = UUID.randomUUID().toString().replace("-", "");
        stateStore.put(state, new StateEntry(Instant.now().getEpochSecond() + STATE_TTL_SECONDS, null));
        return org.springframework.http.ResponseEntity.ok(com.wecombft.shared.web.ApiResponse.ok(
            new QrConfigResponse(
                wecomProperties.corpId(),
                wecomProperties.agentId(),
                resolveRedirectUri(),
                state),
            com.wecombft.shared.trace.TraceIds.currentOrCreate()));
    }

    public record QrConfigResponse(String corpId, String agentId, String redirectUri, String state) {
    }

    @GetMapping("/api/admin/auth/wecom-oauth/callback")
    public ResponseEntity<Void> callback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        StateEntry entry = stateStore.remove(state);
        if (entry == null || entry.expireAt < Instant.now().getEpochSecond()) {
            return redirect(buildFrontendErrorUri("OAUTH_STATE_INVALID", "state 非法或已过期", null));
        }
        try {
            TestLoginResponse login = authApplicationService.wecomLogin(code);
            return redirect(buildFrontendSuccessUri(login, entry.from));
        } catch (com.wecombft.shared.web.ApiException e) {
            return redirect(buildFrontendErrorUri(e.code(), e.getMessage(), entry.from));
        }
    }

    private void purgeExpiredStates() {
        long now = Instant.now().getEpochSecond();
        stateStore.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
    }

    private String resolveRedirectUri() {
        String configured = wecomProperties.oauthRedirectUri();
        if (configured == null || configured.isBlank()) {
            return "http://localhost:8080/api/admin/auth/wecom-oauth/callback";
        }
        return configured;
    }

    private ResponseEntity<Void> redirect(URI uri) {
        return ResponseEntity.status(302).location(uri).build();
    }

    private URI buildFrontendSuccessUri(TestLoginResponse login, String from) {
        String token = URLEncoder.encode(login.accessToken(), StandardCharsets.UTF_8);
        String userNo = URLEncoder.encode(login.userNo(), StandardCharsets.UTF_8);
        if ("sidebar".equals(from)) {
            return URI.create("/h5/wecom-sidebar/?token=" + token + "&user_no=" + userNo);
        }
        return URI.create("/admin/#/oauth-success?token=" + token + "&user_no=" + userNo);
    }

    private URI buildFrontendErrorUri(String code, String message, String from) {
        String c = URLEncoder.encode(code == null ? "ERROR" : code, StandardCharsets.UTF_8);
        String m = URLEncoder.encode(message == null ? "" : message, StandardCharsets.UTF_8);
        if ("sidebar".equals(from)) {
            return URI.create("/h5/wecom-sidebar/?oauth_error=" + c + "&oauth_message=" + m);
        }
        return URI.create("/admin/#/login?oauth_error=" + c + "&oauth_message=" + m);
    }

    private record StateEntry(long expireAt, String from) {
    }

}
