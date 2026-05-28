package com.wecombft.interfaces.app;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.student.AppNotificationApplicationService;
import com.wecombft.application.student.AppNotificationApplicationService.NotificationView;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
@RequestMapping("/api/app/notifications")
public class AppNotificationController {

    private final AppNotificationApplicationService service;

    public AppNotificationController(AppNotificationApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationView>>> list(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.listMine(authorization, pageNo, pageSize), TraceIds.currentOrCreate()));
    }

    @PostMapping("/{notification_id}/read")
    public ResponseEntity<ApiResponse<NotificationView>> markRead(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("notification_id") long notificationId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.markRead(authorization, notificationId), TraceIds.currentOrCreate()));
    }
}
