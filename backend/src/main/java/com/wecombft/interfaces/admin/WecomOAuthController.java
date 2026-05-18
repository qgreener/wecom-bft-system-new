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

    private final ConcurrentHashMap<String, Long> stateStore = new ConcurrentHashMap<>();

    public WecomOAuthController(AdminAuthApplicationService authApplicationService, WecomProperties wecomProperties) {
        this.authApplicationService = authApplicationService;
        this.wecomProperties = wecomProperties;
    }

    @GetMapping("/api/admin/auth/wecom-oauth/start-redirect")
    public ResponseEntity<Void> startRedirect() {
        purgeExpiredStates();
        String state = UUID.randomUUID().toString().replace("-", "");
        stateStore.put(state, Instant.now().getEpochSecond() + STATE_TTL_SECONDS);
        String redirectUri = resolveRedirectUri();
        String authorizeUrl = authApplicationService.buildWecomOAuthStartUrl(state, redirectUri);
        return redirect(URI.create(authorizeUrl));
    }

    @GetMapping("/api/admin/auth/wecom-oauth/callback")
    public ResponseEntity<Void> callback(
        @RequestParam("code") String code,
        @RequestParam("state") String state
    ) {
        Long expireAt = stateStore.remove(state);
        if (expireAt == null || expireAt < Instant.now().getEpochSecond()) {
            return redirect(buildFrontendErrorUri("OAUTH_STATE_INVALID", "state 非法或已过期"));
        }
        try {
            TestLoginResponse login = authApplicationService.wecomLogin(code);
            return redirect(buildFrontendSuccessUri(login));
        } catch (com.wecombft.shared.web.ApiException e) {
            return redirect(buildFrontendErrorUri(e.code(), e.getMessage()));
        }
    }

    private void purgeExpiredStates() {
        long now = Instant.now().getEpochSecond();
        stateStore.entrySet().removeIf(entry -> entry.getValue() < now);
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

    private URI buildFrontendSuccessUri(TestLoginResponse login) {
        String token = URLEncoder.encode(login.accessToken(), StandardCharsets.UTF_8);
        String userNo = URLEncoder.encode(login.userNo(), StandardCharsets.UTF_8);
        return URI.create("/admin/#/oauth-success?token=" + token + "&user_no=" + userNo);
    }

    private URI buildFrontendErrorUri(String code, String message) {
        String c = URLEncoder.encode(code == null ? "ERROR" : code, StandardCharsets.UTF_8);
        String m = URLEncoder.encode(message == null ? "" : message, StandardCharsets.UTF_8);
        return URI.create("/admin/#/login?oauth_error=" + c + "&oauth_message=" + m);
    }

}
