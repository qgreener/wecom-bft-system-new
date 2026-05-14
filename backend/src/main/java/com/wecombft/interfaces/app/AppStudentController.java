package com.wecombft.interfaces.app;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.student.AppStudentApplicationService;
import com.wecombft.application.student.AppStudentApplicationService.StudentMeResponse;
import com.wecombft.application.student.AppStudentApplicationService.TradePrecheckResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class AppStudentController {

    private final AppStudentApplicationService appStudentApplicationService;

    public AppStudentController(AppStudentApplicationService appStudentApplicationService) {
        this.appStudentApplicationService = appStudentApplicationService;
    }

    @GetMapping("/api/app/students/me")
    public ResponseEntity<ApiResponse<StudentMeResponse>> me(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(ApiResponse.ok(
            appStudentApplicationService.me(authorization),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/app/trade/precheck")
    public ResponseEntity<ApiResponse<TradePrecheckResponse>> tradePrecheck(
        @RequestHeader("Authorization") String authorization
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            appStudentApplicationService.tradePrecheck(authorization),
            TraceIds.currentOrCreate()));
    }
}
