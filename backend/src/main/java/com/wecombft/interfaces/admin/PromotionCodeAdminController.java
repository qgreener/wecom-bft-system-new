package com.wecombft.interfaces.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.crm.PromotionCodeService;
import com.wecombft.application.crm.PromotionCodeService.PromotionCodeView;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class PromotionCodeAdminController {

    private final PromotionCodeService promotionCodeService;

    public PromotionCodeAdminController(PromotionCodeService promotionCodeService) {
        this.promotionCodeService = promotionCodeService;
    }

    @GetMapping("/api/admin/promotion-codes")
    @RequirePermission("crm:lead:read")
    public ResponseEntity<ApiResponse<List<PromotionCodeView>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(promotionCodeService.listAll(), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/promotion-codes")
    @RequirePermission("crm:lead:write")
    public ResponseEntity<ApiResponse<PromotionCodeView>> create(@RequestBody PromotionCodeRequest body) {
        String name = body == null ? null : body.name();
        String channel = body == null ? null : body.channel();
        PromotionCodeView view = promotionCodeService.create(name, channel);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(view, TraceIds.currentOrCreate()));
    }

    public record PromotionCodeRequest(String name, String channel) {
    }
}
