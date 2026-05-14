package com.wecombft.interfaces.app;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestHeader;

import com.wecombft.application.student.AppStudentApplicationService;
import com.wecombft.interfaces.dto.student.AppWechatLoginRequest;
import com.wecombft.interfaces.dto.student.AppWechatLoginResponse;
import com.wecombft.interfaces.dto.student.PhoneAuthorizeRequest;
import com.wecombft.interfaces.dto.student.PhoneAuthorizeResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/app/auth")
public class AppAuthController {

    private final AppStudentApplicationService appStudentApplicationService;

    public AppAuthController(AppStudentApplicationService appStudentApplicationService) {
        this.appStudentApplicationService = appStudentApplicationService;
    }

    @PostMapping("/wechat-login")
    public ResponseEntity<ApiResponse<AppWechatLoginResponse>> wechatLogin(
        @RequestBody AppWechatLoginRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            appStudentApplicationService.wechatLogin(command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/phone-authorize")
    public ResponseEntity<ApiResponse<PhoneAuthorizeResponse>> phoneAuthorize(
        @RequestHeader("Authorization") String authorization,
        @RequestBody PhoneAuthorizeRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            appStudentApplicationService.authorizePhone(authorization, command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
