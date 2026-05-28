package com.wecombft.interfaces.admin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wecombft.application.CreationResult;
import com.wecombft.application.command.finance.ReconciliationImportCommand;
import com.wecombft.application.command.finance.ReconciliationRecordCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.infrastructure.security.AdminPrincipalContext;
import com.wecombft.infrastructure.security.RequireAnyPermission;
import com.wecombft.infrastructure.security.RequirePermission;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchPage;
import com.wecombft.interfaces.dto.finance.ReconciliationBatchResponse;
import com.wecombft.interfaces.dto.finance.ReconciliationCheckRequest;
import com.wecombft.interfaces.dto.finance.ReconciliationImportRequest;
import com.wecombft.interfaces.dto.finance.ReconciliationRecordResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiException;
import com.wecombft.shared.web.ApiResponse;

@RestController
public class ReconciliationAdminController {

    private final AfterSalesFinanceApplicationService service;

    public ReconciliationAdminController(AfterSalesFinanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/api/admin/reconciliation/batches")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> createReconciliationBatch(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody ReconciliationImportRequest command
    ) {
        CreationResult<ReconciliationBatchResponse> result = service.importReconciliation(
            AdminPrincipalContext.currentOrNull(), idempotencyKey, command == null ? null : command.toCommand());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    @PostMapping(path = "/api/admin/reconciliation/batches/upload", consumes = "multipart/form-data")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> uploadReconciliationBatch(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestParam("file") MultipartFile file,
        @RequestParam("bill_month") String billMonth,
        @RequestParam(value = "bill_source", required = false) String billSource
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "请选择对账单文件");
        }
        List<ReconciliationRecordCommand> records;
        try {
            records = parseBillCsv(file);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "对账单解析失败：" + e.getMessage());
        }
        ReconciliationImportCommand command = new ReconciliationImportCommand(
            billMonth,
            billSource == null || billSource.isBlank() ? "WECHAT_PAY" : billSource,
            file.getOriginalFilename(),
            null,
            records);
        CreationResult<ReconciliationBatchResponse> result = service.importReconciliation(
            AdminPrincipalContext.currentOrNull(), idempotencyKey, command);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.created(result.response(), TraceIds.currentOrCreate()));
    }

    private List<ReconciliationRecordCommand> parseBillCsv(MultipartFile file) throws IOException {
        List<ReconciliationRecordCommand> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = readNextNonEmptyLine(reader);
            if (headerLine == null) {
                throw new IOException("对账单为空");
            }
            // 兼容 BOM
            if (!headerLine.isEmpty() && headerLine.charAt(0) == '﻿') {
                headerLine = headerLine.substring(1);
            }
            String[] headers = splitCsv(headerLine);
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndex.put(headers[i].trim(), i);
            }
            // 兼容微信支付商户结算单中文列与英文小写列
            int colMerchant = pickIndex(columnIndex, "商户订单号", "merchant_order_no");
            int colExternal = pickIndex(columnIndex, "外部交易号", "微信交易号", "external_transaction_no");
            int colAmount = pickIndex(columnIndex, "支付金额(元)", "支付金额", "金额(元)", "金额", "bill_amount", "amount");
            int colFee = pickIndex(columnIndex, "手续费(元)", "手续费", "fee_amount", "fee");
            int colType = pickIndex(columnIndex, "记录类型", "record_type", "type");
            if (colMerchant < 0) {
                throw new IOException("缺少商户订单号列");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = splitCsv(line);
                String merchant = safeGet(cols, colMerchant);
                if (merchant == null || merchant.isBlank()) continue;
                String external = safeGet(cols, colExternal);
                String amountText = safeGet(cols, colAmount);
                String feeText = safeGet(cols, colFee);
                String typeText = safeGet(cols, colType);
                Long billAmountCent = parseYuanToCent(amountText);
                Long feeAmountCent = parseYuanToCent(feeText);
                String recordType = (typeText == null || typeText.isBlank()) ? "PAYMENT" : typeText.trim().toUpperCase();
                if (recordType.contains("退款") || "REFUND".equals(recordType)) {
                    recordType = "REFUND";
                } else {
                    recordType = "PAYMENT";
                }
                records.add(new ReconciliationRecordCommand(
                    recordType, merchant.trim(), external == null ? null : external.trim(), billAmountCent, feeAmountCent));
            }
        }
        if (records.isEmpty()) {
            throw new IOException("对账单中未识别到有效记录行");
        }
        return records;
    }

    private static String readNextNonEmptyLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) return line;
        }
        return null;
    }

    private static int pickIndex(Map<String, Integer> headers, String... candidates) {
        for (String name : candidates) {
            Integer idx = headers.get(name);
            if (idx != null) return idx;
        }
        return -1;
    }

    private static String safeGet(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return null;
        return cols[idx];
    }

    private static Long parseYuanToCent(String text) {
        if (text == null) return null;
        String trimmed = text.replace("¥", "").replace(",", "").trim();
        if (trimmed.isEmpty()) return null;
        try {
            double yuan = Double.parseDouble(trimmed);
            return Math.round(yuan * 100);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /**
     * 简单 CSV 切分：支持双引号包裹字段中的逗号。
     */
    private static String[] splitCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
            } else if (c == ',' && !inQuote) {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString());
        return result.toArray(new String[0]);
    }

    @GetMapping("/api/admin/reconciliation/batches")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchPage>> reconciliationBatches() {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationBatches(), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliation/batches/{batch_id}")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> reconciliationBatchDetail(@PathVariable("batch_id") long batchId) {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationDetail(batchId), TraceIds.currentOrCreate()));
    }

    @GetMapping("/api/admin/reconciliation/batches/{batch_id}/records")
    @RequirePermission("finance:reconciliation:write")
    public ResponseEntity<ApiResponse<ReconciliationBatchResponse>> reconciliationBatchRecords(@PathVariable("batch_id") long batchId) {
        return ResponseEntity.ok(ApiResponse.ok(service.reconciliationDetail(batchId), TraceIds.currentOrCreate()));
    }

    @PostMapping("/api/admin/reconciliation/records/{reconciliation_id}/check")
    @RequireAnyPermission({"finance:reconciliation:check", "finance:reconciliation:write"})
    public ResponseEntity<ApiResponse<ReconciliationRecordResponse>> checkReconciliationRecord(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @PathVariable("reconciliation_id") long reconciliationId,
        @RequestBody ReconciliationCheckRequest command
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
            service.checkReconciliationRecord(
                AdminPrincipalContext.currentOrNull(),
                idempotencyKey,
                reconciliationId,
                command == null ? null : command.toCommand()),
            TraceIds.currentOrCreate()));
    }
}
