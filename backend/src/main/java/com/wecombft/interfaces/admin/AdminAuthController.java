package com.wecombft.interfaces.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.iam.AdminAuthApplicationService;
import com.wecombft.application.iam.AdminAuthApplicationService.CurrentUserResponse;
import com.wecombft.application.iam.AdminAuthApplicationService.TestLoginResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthApplicationService authApplicationService;

    public AdminAuthController(AdminAuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @PostMapping("/test-login")
    public ResponseEntity<ApiResponse<TestLoginResponse>> testLogin(@RequestBody TestLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
            authApplicationService.testLogin(request.userNo()),
            TraceIds.currentOrCreate()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(
            authApplicationService.currentUser(authorization),
            TraceIds.currentOrCreate()));
    }

    public record TestLoginRequest(String userNo) {
    }
}
