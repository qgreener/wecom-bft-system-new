package com.wecombft.interfaces.h5;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.wecom.WecomJsapiSignatureService;
import com.wecombft.application.wecom.WecomJsapiSignatureService.JsapiSignaturePayload;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

/**
 * 企微 JS-SDK 签名（公开接口；签名值本身依赖 url + nonce + ticket，攻击者无法伪造）。
 * 路径走 /api/h5/** 而不是 /api/wecom/sidebar/**，因为签名要在 H5 还没拿到登录态时调用。
 */
@RestController
public class WecomJsapiSignatureController {

    private final WecomJsapiSignatureService signatureService;

    public WecomJsapiSignatureController(WecomJsapiSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @GetMapping("/api/h5/wecom/jsapi-signature")
    public ResponseEntity<ApiResponse<JsapiSignaturePayload>> sign(
        @RequestParam("url") String url
    ) {
        return ResponseEntity.ok(ApiResponse.ok(signatureService.sign(url), TraceIds.currentOrCreate()));
    }
}
