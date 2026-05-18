package com.wecombft.interfaces.admin;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wecombft.application.CreationResult;
import com.wecombft.application.file.FileApplicationService;
import com.wecombft.application.file.FileApplicationService.FileDownloadPayload;
import com.wecombft.infrastructure.persistence.file.FileAssetRecord;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.interfaces.dto.file.FileAssetResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class FileAdminController {

    private final FileApplicationService fileApplicationService;

    public FileAdminController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping(value = "/api/admin/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireAnyPermission({
        "tax:invoice:write", "invoice:read", "refund:review:write",
        "accounting:material:write", "finance:reconciliation:write",
        "fulfillment:shipment:write", "purchase:order:write", "system:config:write"
    })
    public ResponseEntity<ApiResponse<FileAssetResponse>> uploadFile(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestPart("file") MultipartFile file,
        @RequestParam("biz_type") String bizType,
        @RequestParam("biz_id") long bizId,
        @RequestParam(value = "order_id", required = false) Long orderId
    ) {
        CreationResult<FileAssetResponse> result = fileApplicationService.uploadFile(
            AdminPrincipalContext.currentOrNull(), idempotencyKey, file, bizType, bizId, orderId);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/files/{file_no}/download")
    @RequireAnyPermission({
        "tax:invoice:write", "invoice:read", "refund:review:write",
        "accounting:material:write", "finance:reconciliation:write",
        "fulfillment:shipment:write", "purchase:order:write", "system:config:write"
    })
    public ResponseEntity<InputStreamResource> downloadFile(
        @PathVariable("file_no") String fileNo,
        @RequestParam(value = "download_reason", required = false, defaultValue = "管理端下载") String downloadReason
    ) {
        FileDownloadPayload payload = fileApplicationService.prepareDownload(
            AdminPrincipalContext.currentOrNull(), fileNo, downloadReason);
        FileAssetRecord record = payload.record();
        HttpHeaders headers = new HttpHeaders();
        String encodedName = java.net.URLEncoder.encode(record.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        headers.setContentDispositionFormData("attachment", record.fileName());
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + record.fileName() + "\"; filename*=UTF-8''" + encodedName);
        headers.set("X-File-No", record.fileNo());
        MediaType contentType = parseMediaType(record.fileType());
        return ResponseEntity.ok()
            .headers(headers)
            .contentLength(record.fileSize())
            .contentType(contentType)
            .body(new InputStreamResource(payload.stream()));
    }

    private MediaType parseMediaType(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(fileType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
