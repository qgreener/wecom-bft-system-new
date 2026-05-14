package com.wecombft.interfaces.app;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.ExternalAuthApplicationService;
import com.wecombft.application.iam.ExternalAuthApplicationService.AppWechatLoginCommand;
import com.wecombft.application.iam.ExternalAuthApplicationService.AppWechatLoginResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final ExternalAuthApplicationService externalAuthApplicationService;

    public AppAuthController(ExternalAuthApplicationService externalAuthApplicationService) {
        this.externalAuthApplicationService = externalAuthApplicationService;
    }

    @PostMapping("/wechat-login")
    public ResponseEntity<ApiResponse<AppWechatLoginResponse>> wechatLogin(
        @RequestBody AppWechatLoginCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            externalAuthApplicationService.appWechatLogin(command),
            TraceIds.currentOrCreate()));
    }
}
