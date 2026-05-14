package com.wecombft.interfaces.h5;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.crm.LeadApplicationService;
import com.wecombft.interfaces.dto.crm.LeadResponse;
import com.wecombft.interfaces.dto.crm.PublicLeadRequest;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class PublicLeadController {

    private final LeadApplicationService leadApplicationService;

    public PublicLeadController(LeadApplicationService leadApplicationService) {
        this.leadApplicationService = leadApplicationService;
    }

    @PostMapping("/api/h5/lead/leads")
    public ResponseEntity<ApiResponse<LeadResponse>> create(@RequestBody PublicLeadRequest command) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(leadApplicationService.createPublicLead(command == null ? null : command.toCommand()), TraceIds.currentOrCreate()));
    }
}
