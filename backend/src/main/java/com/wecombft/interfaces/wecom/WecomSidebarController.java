package com.wecombft.interfaces.wecom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.crm.LeadApplicationService;
import com.wecombft.application.crm.LeadApplicationService.FollowRecordCommand;
import com.wecombft.application.crm.LeadApplicationService.FollowRecordResponse;
import com.wecombft.application.crm.LeadApplicationService.LeadResponse;
import com.wecombft.application.crm.LeadApplicationService.LeadSaveCommand;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class WecomSidebarController {

    private final LeadApplicationService leadApplicationService;

    public WecomSidebarController(LeadApplicationService leadApplicationService) {
        this.leadApplicationService = leadApplicationService;
    }

    @PostMapping("/api/wecom/sidebar/leads")
    @RequirePermission("crm:lead:write")
    public ResponseEntity<ApiResponse<LeadResponse>> createLead(@RequestBody LeadSaveCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(leadApplicationService.createAdminLead(command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/wecom/sidebar/leads/{lead_id}/follow-records")
    @RequirePermission("crm:lead:write")
    public ResponseEntity<ApiResponse<FollowRecordResponse>> follow(
        @PathVariable("lead_id") long leadId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody FollowRecordCommand command
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(leadApplicationService.createFollowRecord(leadId, idempotencyKey, command), TraceIds.currentOrCreate()));
    }
}
