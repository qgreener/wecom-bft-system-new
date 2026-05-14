package com.wecombft.interfaces.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.crm.LeadApplicationService;
import com.wecombft.application.crm.LeadApplicationService.FollowRecordCommand;
import com.wecombft.application.crm.LeadApplicationService.FollowRecordResponse;
import com.wecombft.application.crm.LeadApplicationService.LeadPage;
import com.wecombft.application.crm.LeadApplicationService.LeadResponse;
import com.wecombft.application.crm.LeadApplicationService.LeadSaveCommand;
import com.wecombft.application.crm.LeadApplicationService.LeadStatusCommand;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class LeadAdminController {

    private final LeadApplicationService leadApplicationService;

    public LeadAdminController(LeadApplicationService leadApplicationService) {
        this.leadApplicationService = leadApplicationService;
    }

    @GetMapping("/api/admin/leads")
    @RequirePermission("crm:lead:read")
    public ResponseEntity<ApiResponse<LeadPage>> leads(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "page_no", required = false) Integer pageNo,
        @RequestParam(value = "page_size", required = false) Integer pageSize
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            leadApplicationService.searchAdminLeads(keyword, status, pageNo, pageSize),
            TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/leads")
    @RequirePermission("crm:lead:write")
    public ResponseEntity<ApiResponse<LeadResponse>> saveLead(@RequestBody LeadSaveCommand command) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(leadApplicationService.createAdminLead(command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/leads/{lead_id}/follow-records")
    @RequirePermission("crm:lead:write")
    public ResponseEntity<ApiResponse<FollowRecordResponse>> follow(
        @PathVariable("lead_id") long leadId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody FollowRecordCommand command
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(leadApplicationService.createFollowRecord(leadId, idempotencyKey, command), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/leads/{lead_id}/status")
    @RequirePermission("crm:lead:write")
    public ResponseEntity<ApiResponse<LeadResponse>> status(
        @PathVariable("lead_id") long leadId,
        @RequestBody LeadStatusCommand command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            leadApplicationService.updateLeadStatus(leadId, command),
            TraceIds.currentOrCreate()));
    }
}
