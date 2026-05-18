package com.wecombft.application.file;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.wecombft.application.CreationResult;
import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.persistence.file.FileAssetRecord;
import com.wecombft.infrastructure.persistence.file.FileAssetRepository;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.infrastructure.storage.FileStorageAdapter;
import com.wecombft.interfaces.dto.file.FileAssetResponse;
import com.wecombft.interfaces.health.FileStorageProperties;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class FileApplicationService {

    private static final Map<String, List<String>> BIZ_PERMISSION_MAP = Map.of(
        "INVOICE", List.of("tax:invoice:write", "invoice:read"),
        "RED_INVOICE", List.of("tax:invoice:write"),
        "REFUND_VOUCHER", List.of("refund:review:write", "accounting:material:write"),
        "ACCOUNTING_MATERIAL", List.of("accounting:material:write"),
        "RECONCILIATION_BILL", List.of("finance:reconciliation:write"),
        "WAYBILL", List.of("fulfillment:shipment:write"),
        "PURCHASE_INVOICE", List.of("purchase:order:write", "accounting:material:write")
    );

    private final FileAssetRepository fileAssetRepository;
    private final FileStorageAdapter storageAdapter;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;
    private final FileStorageProperties properties;

    public FileApplicationService(
        FileAssetRepository fileAssetRepository,
        FileStorageAdapter storageAdapter,
        IdGenerator idGenerator,
        AuditLogService auditLogService,
        FileStorageProperties properties
    ) {
        this.fileAssetRepository = fileAssetRepository;
        this.storageAdapter = storageAdapter;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
        this.properties = properties;
    }

    @Transactional
    public CreationResult<FileAssetResponse> uploadFile(
        AdminPrincipal principal,
        String idempotencyKey,
        MultipartFile file,
        String bizType,
        long bizId,
        Long orderId
    ) {
        requireAdmin(principal);
        requireBizPermission(principal, bizType, "upload");
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "上传文件不能为空");
        }
        long maxBytes = properties.maxUploadSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "文件超过 " + properties.maxUploadSizeMb() + "MB 上限");
        }

        String digest = computeDigest(file);
        Optional<FileAssetRecord> existing = fileAssetRepository.findByDigestBiz(digest, bizType, bizId);
        if (existing.isPresent()) {
            return new CreationResult<>(toResponse(existing.get()), false);
        }

        long fileId = idGenerator.nextId();
        String fileNo = "FILE" + Long.toString(Math.abs(fileId % 1_000_000_000L));
        String storageKey = buildStorageKey(bizType, bizId, fileNo, file.getOriginalFilename());
        try (InputStream stream = file.getInputStream()) {
            storageAdapter.store(stream, storageKey, file.getContentType());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED",
                "文件落盘失败: " + e.getMessage());
        }

        String fileType = inferFileType(file.getOriginalFilename(), file.getContentType());
        LocalDateTime now = LocalDateTime.now();
        FileAssetRecord record = new FileAssetRecord(
            fileId,
            fileNo,
            requireFileName(file.getOriginalFilename()),
            fileType,
            storageKey,
            null,
            digest,
            file.getSize(),
            bizType,
            bizId,
            orderId,
            "ACTIVE",
            principal.userId(),
            now,
            now,
            now);
        FileAssetRecord inserted = fileAssetRepository.insert(record);
        auditLogService.writeSuccess(principal, "FILE", "FILE_UPLOAD", "SYS_FILE_ASSET",
            inserted.id(), inserted.fileNo(), orderId,
            "{\"biz_type\":\"" + bizType + "\",\"file_size\":" + file.getSize() + "}");
        return new CreationResult<>(toResponse(inserted), true);
    }

    public FileDownloadPayload prepareDownload(AdminPrincipal principal, String fileNo, String downloadReason) {
        requireAdmin(principal);
        if (downloadReason == null || downloadReason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "下载原因不能为空");
        }
        FileAssetRecord record = fileAssetRepository.findByFileNo(fileNo)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "文件不存在"));
        requireBizPermission(principal, record.bizType(), "download");
        InputStream stream;
        try {
            stream = storageAdapter.read(record.storageKey());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.GONE, "FILE_GONE", "文件已失效: " + e.getMessage());
        }
        auditLogService.writeSuccess(principal, "FILE", "FILE_DOWNLOAD", "SYS_FILE_ASSET",
            record.id(), record.fileNo(), record.orderId(),
            "{\"reason\":\"" + sanitize(downloadReason) + "\"}");
        return new FileDownloadPayload(stream, record);
    }

    public FileAssetResponse fileDetail(AdminPrincipal principal, String fileNo) {
        requireAdmin(principal);
        FileAssetRecord record = fileAssetRepository.findByFileNo(fileNo)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "文件不存在"));
        return toResponse(record);
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
    }

    private void requireBizPermission(AdminPrincipal principal, String bizType, String operation) {
        List<String> allowed = BIZ_PERMISSION_MAP.get(bizType);
        if (allowed == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "未知 biz_type: " + bizType);
        }
        List<String> userPermissions = principal.permissionView().permissionCodes();
        boolean ok = allowed.stream().anyMatch(userPermissions::contains)
            || userPermissions.contains("system:config:write");
        if (!ok) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "无权" + operation + " " + bizType + " 类型文件");
        }
    }

    private String computeDigest(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int n;
            while ((n = is.read(buffer)) > 0) {
                md.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DIGEST_FAILED",
                "无法计算文件摘要: " + e.getMessage());
        }
    }

    private String buildStorageKey(String bizType, long bizId, String fileNo, String originalName) {
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot > 0 && dot < originalName.length() - 1) {
                ext = originalName.substring(dot).toLowerCase();
            }
        }
        return bizType.toLowerCase() + "/" + bizId + "/" + fileNo + ext;
    }

    private String inferFileType(String fileName, String contentType) {
        if (contentType != null && !contentType.isBlank()) {
            return contentType;
        }
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                return fileName.substring(dot + 1).toUpperCase();
            }
        }
        return "application/octet-stream";
    }

    private String requireFileName(String original) {
        if (original == null || original.isBlank()) {
            return "untitled";
        }
        return original;
    }

    private FileAssetResponse toResponse(FileAssetRecord record) {
        return new FileAssetResponse(
            record.id(),
            record.fileNo(),
            record.fileName(),
            record.fileType(),
            record.storageKey(),
            record.accessUrl(),
            record.fileDigest(),
            record.fileSize(),
            record.bizType(),
            record.bizId(),
            record.orderId(),
            record.status(),
            record.uploadedBy(),
            record.uploadedAt());
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    public record FileDownloadPayload(InputStream stream, FileAssetRecord record) {
    }
}
