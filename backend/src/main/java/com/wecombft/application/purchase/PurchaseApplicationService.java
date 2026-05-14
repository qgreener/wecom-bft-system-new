package com.wecombft.application.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.command.purchase.ApprovalActionCommand;
import com.wecombft.application.command.purchase.PurchaseCreateCommand;
import com.wecombft.application.command.purchase.PurchaseInputInvoiceCommand;
import com.wecombft.application.command.purchase.PurchaseItemCommand;
import com.wecombft.application.command.purchase.PurchaseReceiveCommand;
import com.wecombft.application.command.purchase.ReceivedItemCommand;
import com.wecombft.application.command.purchase.SupplierConfirmCommand;
import com.wecombft.application.command.purchase.SupplierLogisticsCommand;
import com.wecombft.application.command.purchase.SupplierRejectCommand;
import com.wecombft.interfaces.dto.purchase.PurchaseItemResponse;
import com.wecombft.interfaces.dto.purchase.PurchasePage;
import com.wecombft.interfaces.dto.purchase.PurchaseReceiptResponse;
import com.wecombft.interfaces.dto.purchase.PurchaseResponse;
import com.wecombft.interfaces.dto.purchase.PurchaseSummary;
import com.wecombft.interfaces.dto.purchase.ReceiptRow;
import com.wecombft.interfaces.dto.purchase.StockFlowResponse;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

import com.wecombft.application.CreationResult;
@Service
public class PurchaseApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public PurchaseApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        ObjectMapper objectMapper,
        AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CreationResult<PurchaseResponse> createPurchase(AdminPrincipal principal, String idempotencyKey, PurchaseCreateCommand command) {
        requireAdmin(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<PurchaseRow> existing = findPurchaseByIdempotency(key);
        if (existing.isPresent()) {
            return new CreationResult<>(purchaseDetail(existing.get().id()), false);
        }
        long supplierId = positive(command == null ? null : command.supplierId(), "供货商不能为空");
        requireActiveSupplier(supplierId);
        List<PurchaseItemCommand> items = command.purchaseItems() == null ? List.of() : command.purchaseItems();
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "采购明细不能为空");
        }
        long totalAmountCent = 0;
        List<PreparedPurchaseItem> preparedItems = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseItemCommand item : items) {
            long skuId = positive(item.skuId(), "采购 SKU 不能为空");
            int quantity = positiveInt(item.quantity(), "采购数量必须大于 0");
            long unitPriceCent = nonNegative(item.unitPriceCent(), "采购单价不能为负");
            SkuStock sku = requireSkuStock(skuId);
            if (!"ACTIVE".equals(sku.status())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "SKU 已停用");
            }
            long lineAmount = unitPriceCent * quantity;
            totalAmountCent += lineAmount;
            preparedItems.add(new PreparedPurchaseItem(lineNo++, sku, quantity, unitPriceCent, lineAmount));
        }
        long threshold = purchaseThresholdCent();
        boolean largePurchase = totalAmountCent > threshold;
        long purchaseId = idGenerator.nextId();
        String purchaseNo = "PUR" + purchaseId;
        Long approvalId = largePurchase ? idGenerator.nextId() : null;
        jdbcTemplate.update(
            """
            insert into purchase_order (
                id, purchase_no, supplier_id, applicant_user_id, total_amount_cent,
                purchase_status, input_invoice_status, approval_id, threshold_snapshot_cent,
                expected_arrival_date, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, 'NOT_INVOICED', ?, ?, ?, ?, ?, ?)
            """,
            purchaseId,
            purchaseNo,
            supplierId,
            principal.userId(),
            totalAmountCent,
            largePurchase ? "APPROVING" : "WAIT_CONFIRM",
            approvalId,
            threshold,
            command.expectedArrivalDate(),
            key,
            principal.userId(),
            principal.userId());
        for (PreparedPurchaseItem item : preparedItems) {
            jdbcTemplate.update(
                """
                insert into purchase_order_item (
                    id, purchase_id, purchase_no, line_no, sku_id, sku_no, sku_name,
                    quantity, unit_price_cent, total_amount_cent, received_quantity,
                    created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                """,
                idGenerator.nextId(),
                purchaseId,
                purchaseNo,
                item.lineNo(),
                item.sku().skuId(),
                item.sku().skuNo(),
                item.sku().skuName(),
                item.quantity(),
                item.unitPriceCent(),
                item.totalAmountCent(),
                principal.userId(),
                principal.userId());
        }
        if (largePurchase) {
            jdbcTemplate.update(
                """
                insert into approval_record (
                    id, approval_no, approval_type, title, applicant_user_id, related_object_type,
                    related_object_id, related_object_no, status, submit_reason, submitted_at,
                    amount_snapshot_cent, created_by, updated_by
                ) values (?, ?, 'PURCHASE_LARGE', ?, ?, 'PURCHASE_ORDER', ?, ?, 'PENDING', ?, ?, ?, ?, ?)
                """,
                approvalId,
                "APR" + approvalId,
                "大额采购审批 " + purchaseNo,
                principal.userId(),
                purchaseId,
                purchaseNo,
                defaultString(command.submitReason(), "大额采购审批"),
                LocalDateTime.now(),
                totalAmountCent,
                principal.userId(),
                principal.userId());
        }
        auditLogService.writeSuccess(
            principal,
            "PURCHASE",
            "PURCHASE_CREATE",
            "PURCHASE_ORDER",
            purchaseId,
            purchaseNo,
            null,
            "{\"purchase_status\":\"" + (largePurchase ? "APPROVING" : "WAIT_CONFIRM") + "\",\"total_amount_cent\":" + totalAmountCent + "}");
        return new CreationResult<>(purchaseDetail(purchaseId), true);
    }

    public PurchasePage purchases(String status, Integer pageNo, Integer pageSize) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (status != null && !status.isBlank()) {
            where.append(" and purchase_status = ?");
            args.add(status.trim().toUpperCase());
        }
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        long total = countRows("select count(*) from purchase_order" + where, args);
        StringBuilder sql = new StringBuilder(
            """
            select id, purchase_no, supplier_id, applicant_user_id, total_amount_cent, purchase_status,
                   input_invoice_status, approval_id, threshold_snapshot_cent, expected_arrival_date,
                   supplier_confirm_at, supplier_reject_reason, logistics_company_name, tracking_no,
                   received_at, receiver_user_id, idempotency_key, input_invoice_no,
                   input_invoice_amount_cent, input_invoice_file, created_at
            from purchase_order
            """)
            .append(where);
        List<Object> queryArgs = new ArrayList<>(args);
        sql.append(" order by created_at desc, id desc limit ? offset ?");
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        return new PurchasePage(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapPurchase(rs).toSummary(), queryArgs.toArray()), page, size, total);
    }

    public PurchaseResponse purchaseDetail(long purchaseId) {
        PurchaseRow purchase = requirePurchase(purchaseId);
        return purchase.toResponse(purchaseItems(purchaseId), purchaseReceipts(purchaseId), stockFlowsByPurchase(purchaseId));
    }

    @Transactional
    public PurchaseResponse backfillInputInvoice(AdminPrincipal principal, long purchaseId, String idempotencyKey, PurchaseInputInvoiceCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        PurchaseRow purchase = requirePurchase(purchaseId);
        String invoiceNo = requireText(command == null ? null : command.inputInvoiceNo(), "进项票号不能为空");
        long invoiceAmountCent = nonNegative(command.inputInvoiceAmountCent(), "进项票金额不能为负");
        String invoiceFile = requireText(command.inputInvoiceFile(), "进项票文件不能为空");
        if (invoiceAmountCent > purchase.totalAmountCent()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "进项票金额不能超过采购金额");
        }
        if ("INVOICED".equals(purchase.inputInvoiceStatus())) {
            if (invoiceNo.equals(purchase.inputInvoiceNo())
                && invoiceAmountCent == nullToZero(purchase.inputInvoiceAmountCent())
                && invoiceFile.equals(purchase.inputInvoiceFile())) {
                return purchaseDetail(purchase.id());
            }
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单已回填进项票");
        }
        if (!"COMPLETED".equals(purchase.purchaseStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购未完成不可回填进项票");
        }
        jdbcTemplate.update(
            """
            update purchase_order
            set input_invoice_status = 'INVOICED',
                input_invoice_no = ?,
                input_invoice_amount_cent = ?,
                input_invoice_file = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and input_invoice_status = 'NOT_INVOICED'
            """,
            invoiceNo,
            invoiceAmountCent,
            invoiceFile,
            LocalDateTime.now(),
            principal.userId(),
            purchase.id());
        auditLogService.writeSuccess(
            principal,
            "PURCHASE",
            "PURCHASE_INPUT_INVOICE_BACKFILL",
            "PURCHASE_ORDER",
            purchase.id(),
            purchase.purchaseNo(),
            null,
            "{\"input_invoice_no\":\"" + jsonSafe(invoiceNo) + "\",\"input_invoice_amount_cent\":" + invoiceAmountCent + "}");
        return purchaseDetail(purchase.id());
    }

    @Transactional
    public PurchaseResponse approvePurchase(AdminPrincipal principal, long approvalId, ApprovalActionCommand command) {
        requireAdmin(principal);
        String action = normalizeChoice(command == null ? null : command.action(), List.of("APPROVE", "REJECT"), "审批动作非法");
        PurchaseRow purchase = findPurchaseByApprovalId(approvalId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "采购审批不存在"));
        if (!"APPROVING".equals(purchase.purchaseStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单当前状态不可审批回调");
        }
        String approvalStatus = "APPROVE".equals(action) ? "APPROVED" : "REJECTED";
        String purchaseStatus = "APPROVE".equals(action) ? "WAIT_CONFIRM" : "APPROVAL_REJECTED";
        jdbcTemplate.update(
            """
            update approval_record
            set status = ?,
                approver_user_id = ?,
                approval_comment = ?,
                finished_at = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'PENDING'
            """,
            approvalStatus,
            principal.userId(),
            blankToNull(command.comment()),
            LocalDateTime.now(),
            LocalDateTime.now(),
            principal.userId(),
            approvalId);
        jdbcTemplate.update(
            """
            update purchase_order
            set purchase_status = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            purchaseStatus,
            LocalDateTime.now(),
            principal.userId(),
            purchase.id());
        auditLogService.writeSuccess(
            principal,
            "PURCHASE",
            "PURCHASE_APPROVAL_" + action,
            "PURCHASE_ORDER",
            purchase.id(),
            purchase.purchaseNo(),
            null,
            "{\"purchase_status\":\"" + purchaseStatus + "\"}");
        return purchaseDetail(purchase.id());
    }

    @Transactional
    public PurchaseResponse supplierConfirm(String authorizationHeader, long purchaseId, String idempotencyKey, SupplierConfirmCommand command) {
        SupplierSession supplier = requireSupplierSession(authorizationHeader);
        requireIdempotencyKey(idempotencyKey);
        PurchaseRow purchase = requireSupplierPurchase(supplier, purchaseId, true);
        if ("CONFIRMED".equals(purchase.purchaseStatus()) || "SHIPPED".equals(purchase.purchaseStatus()) || "COMPLETED".equals(purchase.purchaseStatus())) {
            return purchaseDetail(purchase.id());
        }
        if (!"WAIT_CONFIRM".equals(purchase.purchaseStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单当前状态不可确认");
        }
        jdbcTemplate.update(
            """
            update purchase_order
            set purchase_status = 'CONFIRMED',
                expected_arrival_date = ?,
                supplier_confirm_at = ?,
                updated_at = ?,
                updated_by = null,
                version = version + 1
            where id = ?
            """,
            command == null ? null : command.expectedArrivalDate(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            purchase.id());
        auditLogService.writeSystemSuccess("PURCHASE", "SUPPLIER_CONFIRM", "PURCHASE_ORDER", purchase.id(), purchase.purchaseNo(), null, "{\"supplier_no\":\"" + jsonSafe(supplier.supplierNo()) + "\"}");
        return purchaseDetail(purchase.id());
    }

    @Transactional
    public PurchaseResponse supplierReject(String authorizationHeader, long purchaseId, String idempotencyKey, SupplierRejectCommand command) {
        SupplierSession supplier = requireSupplierSession(authorizationHeader);
        requireIdempotencyKey(idempotencyKey);
        PurchaseRow purchase = requireSupplierPurchase(supplier, purchaseId, true);
        if ("REJECTED".equals(purchase.purchaseStatus())) {
            return purchaseDetail(purchase.id());
        }
        if (!"WAIT_CONFIRM".equals(purchase.purchaseStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单当前状态不可拒绝");
        }
        String reason = requireText(command == null ? null : command.supplierRejectReason(), "拒绝原因不能为空");
        jdbcTemplate.update(
            """
            update purchase_order
            set purchase_status = 'REJECTED',
                supplier_reject_reason = ?,
                updated_at = ?,
                updated_by = null,
                version = version + 1
            where id = ?
            """,
            reason,
            LocalDateTime.now(),
            purchase.id());
        auditLogService.writeSystemSuccess("PURCHASE", "SUPPLIER_REJECT", "PURCHASE_ORDER", purchase.id(), purchase.purchaseNo(), null, "{\"supplier_no\":\"" + jsonSafe(supplier.supplierNo()) + "\"}");
        return purchaseDetail(purchase.id());
    }

    @Transactional
    public PurchaseResponse supplierLogistics(String authorizationHeader, long purchaseId, String idempotencyKey, SupplierLogisticsCommand command) {
        SupplierSession supplier = requireSupplierSession(authorizationHeader);
        requireIdempotencyKey(idempotencyKey);
        PurchaseRow purchase = requireSupplierPurchase(supplier, purchaseId, true);
        if ("SHIPPED".equals(purchase.purchaseStatus()) || "COMPLETED".equals(purchase.purchaseStatus())) {
            return purchaseDetail(purchase.id());
        }
        if (!"CONFIRMED".equals(purchase.purchaseStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单当前状态不可填写物流");
        }
        String logisticsCompanyName = requireText(command == null ? null : command.logisticsCompanyName(), "采购物流公司不能为空");
        String trackingNo = requireText(command.trackingNo(), "采购物流单号不能为空");
        jdbcTemplate.update(
            """
            update purchase_order
            set purchase_status = 'SHIPPED',
                logistics_company_name = ?,
                tracking_no = ?,
                updated_at = ?,
                updated_by = null,
                version = version + 1
            where id = ?
            """,
            logisticsCompanyName,
            trackingNo,
            LocalDateTime.now(),
            purchase.id());
        auditLogService.writeSystemSuccess("PURCHASE", "SUPPLIER_LOGISTICS", "PURCHASE_ORDER", purchase.id(), purchase.purchaseNo(), null, "{\"tracking_no\":\"" + jsonSafe(trackingNo) + "\"}");
        return purchaseDetail(purchase.id());
    }

    public PurchasePage supplierPurchases(String authorizationHeader, String status, Integer pageNo, Integer pageSize) {
        SupplierSession supplier = requireSupplierSession(authorizationHeader);
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(
            """
            where supplier_id = ?
              and purchase_status <> 'APPROVING'
              and purchase_status <> 'APPROVAL_REJECTED'
              and purchase_status <> 'CANCELED'
            """);
        args.add(supplier.supplierId());
        if (status != null && !status.isBlank()) {
            where.append(" and purchase_status = ?");
            args.add(status.trim().toUpperCase());
        }
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        long total = countRows("select count(*) from purchase_order " + where, args);
        StringBuilder sql = new StringBuilder(
            """
            select id, purchase_no, supplier_id, applicant_user_id, total_amount_cent, purchase_status,
                   input_invoice_status, approval_id, threshold_snapshot_cent, expected_arrival_date,
                   supplier_confirm_at, supplier_reject_reason, logistics_company_name, tracking_no,
                   received_at, receiver_user_id, idempotency_key, input_invoice_no,
                   input_invoice_amount_cent, input_invoice_file, created_at
            from purchase_order
            """)
            .append(where);
        List<Object> queryArgs = new ArrayList<>(args);
        sql.append(" order by created_at desc, id desc limit ? offset ?");
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        return new PurchasePage(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapPurchase(rs).toSummary(), queryArgs.toArray()), page, size, total);
    }

    public PurchaseResponse supplierPurchaseDetail(String authorizationHeader, long purchaseId) {
        SupplierSession supplier = requireSupplierSession(authorizationHeader);
        return purchaseDetail(requireSupplierPurchase(supplier, purchaseId, true).id());
    }

    @Transactional
    public CreationResult<PurchaseReceiptResponse> receivePurchase(AdminPrincipal principal, long purchaseId, String idempotencyKey, PurchaseReceiveCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        PurchaseRow purchase = requirePurchase(purchaseId);
        String inboundBatchNo = requireText(command == null ? null : command.inboundBatchNo(), "入库批次不能为空");
        Optional<ReceiptRow> existing = findReceiptByPurchaseAndBatch(purchase.purchaseNo(), inboundBatchNo);
        if (existing.isPresent()) {
            return new CreationResult<>(receiptResponse(existing.get()), false);
        }
        if (!"SHIPPED".equals(purchase.purchaseStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "非已发货采购不可收货");
        }
        Map<Long, Integer> received = new LinkedHashMap<>();
        if (command.receivedItems() != null) {
            for (ReceivedItemCommand item : command.receivedItems()) {
                received.put(positive(item.skuId(), "收货 SKU 不能为空"), positiveInt(item.receivedQuantity(), "收货数量必须大于 0"));
            }
        }
        List<PurchaseItemRow> purchaseItems = purchaseItems(purchaseId);
        for (PurchaseItemRow item : purchaseItems) {
            int receivedQuantity = received.getOrDefault(item.skuId(), 0);
            if (receivedQuantity != item.quantity()) {
                auditLogService.writeFailure(principal, "PURCHASE", "PURCHASE_RECEIVE_QUANTITY_MISMATCH", "PURCHASE_ORDER", purchase.id(), purchase.purchaseNo(), null, "收货数量与采购数量不一致");
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "收货数量与采购数量不一致，需人工确认");
            }
        }
        long receiptId = idGenerator.nextId();
        String receiptNo = "RCT" + receiptId;
        List<Long> flowIds = new ArrayList<>();
        for (PurchaseItemRow item : purchaseItems) {
            StockFlowRow flow = inboundForPurchase(principal, purchase, item, receiptNo, inboundBatchNo);
            flowIds.add(flow.id());
            jdbcTemplate.update("update purchase_order_item set received_quantity = ?, updated_at = ?, updated_by = ? where id = ?",
                item.quantity(),
                LocalDateTime.now(),
                principal.userId(),
                item.id());
        }
        jdbcTemplate.update(
            """
            insert into purchase_receipt (
                id, receipt_no, purchase_id, purchase_no, inbound_batch_no, status,
                received_at, receiver_user_id, stock_flow_ids, remark, created_by, updated_by
            ) values (?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?)
            """,
            receiptId,
            receiptNo,
            purchase.id(),
            purchase.purchaseNo(),
            inboundBatchNo,
            LocalDateTime.now(),
            principal.userId(),
            toJson(flowIds),
            blankToNull(command.remark()),
            principal.userId(),
            principal.userId());
        jdbcTemplate.update(
            """
            update purchase_order
            set purchase_status = 'COMPLETED',
                received_at = ?,
                receiver_user_id = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ?
            """,
            LocalDateTime.now(),
            principal.userId(),
            LocalDateTime.now(),
            principal.userId(),
            purchase.id());
        auditLogService.writeSuccess(principal, "PURCHASE", "PURCHASE_RECEIVE", "PURCHASE_ORDER", purchase.id(), purchase.purchaseNo(), null, "{\"receipt_no\":\"" + receiptNo + "\"}");
        return new CreationResult<>(receiptResponse(findReceiptByPurchaseAndBatch(purchase.purchaseNo(), inboundBatchNo).orElseThrow()), true);
    }

    private StockFlowRow inboundForPurchase(AdminPrincipal principal, PurchaseRow purchase, PurchaseItemRow item, String receiptNo, String inboundBatchNo) {
        String key = "PURCHASE:" + purchase.purchaseNo() + ":" + inboundBatchNo + ":" + item.skuId();
        Optional<StockFlowRow> existing = findStockFlowByIdempotency(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        SkuStock stock = requireSkuStockForUpdate(item.skuId());
        int before = stock.availableStock();
        int after = before + item.quantity();
        long flowId = idGenerator.nextId();
        String flowNo = "STF" + flowId;
        boolean inserted = insertStockFlow(
            flowId,
            flowNo,
            item.skuId(),
            "PURCHASE_RECEIPT",
            purchase.id(),
            receiptNo,
            null,
            null,
            purchase.id(),
            "IN",
            item.quantity(),
            before,
            after,
            principal.userId(),
            key,
            "采购入库");
        if (!inserted) {
            return findStockFlowByIdempotency(key).orElseThrow();
        }
        jdbcTemplate.update(
            "update inventory_sku set current_stock = ?, available_stock = ?, updated_at = ?, updated_by = ?, version = version + 1 where id = ?",
            after,
            after,
            LocalDateTime.now(),
            principal.userId(),
            item.skuId());
        return findStockFlowByIdempotency(key).orElseThrow();
    }

    private boolean insertStockFlow(
        long flowId,
        String flowNo,
        long skuId,
        String bizType,
        long bizId,
        String bizNo,
        Long orderId,
        Long shipmentId,
        Long purchaseId,
        String direction,
        int quantity,
        int before,
        int after,
        Long operatorUserId,
        String idempotencyKey,
        String remark
    ) {
        try {
            jdbcTemplate.update(
                """
                insert into inventory_stock_flow (
                    id, flow_no, sku_id, biz_type, biz_id, biz_no, order_id, shipment_id, purchase_id,
                    direction, quantity, before_stock, after_stock, operator_user_id, occurred_at,
                    idempotency_key, remark
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                flowId,
                flowNo,
                skuId,
                bizType,
                bizId,
                bizNo,
                orderId,
                shipmentId,
                purchaseId,
                direction,
                quantity,
                before,
                after,
                operatorUserId,
                LocalDateTime.now(),
                idempotencyKey,
                blankToNull(remark));
            return true;
        } catch (DuplicateKeyException duplicateKeyException) {
            if (findStockFlowByIdempotency(idempotencyKey).isPresent()) {
                return false;
            }
            throw duplicateKeyException;
        }
    }

    private PurchaseReceiptResponse receiptResponse(ReceiptRow receipt) {
        return new PurchaseReceiptResponse(
            receipt.id(),
            receipt.receiptNo(),
            receipt.purchaseId(),
            receipt.purchaseNo(),
            "COMPLETED",
            receipt.receivedAt(),
            stockFlowIdsForPurchase(receipt.purchaseId()),
            requirePurchase(receipt.purchaseId()).purchaseStatus());
    }

    private SkuStock requireSkuStock(long skuId) {
        return jdbcTemplate.query(
            """
            select id, sku_no, sku_name, status, current_stock, available_stock
            from inventory_sku
            where id = ? and deleted_flag = 0
            """,
            (rs, rowNum) -> new SkuStock(
                rs.getLong("id"),
                rs.getString("sku_no"),
                rs.getString("sku_name"),
                rs.getString("status"),
                rs.getInt("current_stock"),
                rs.getInt("available_stock")),
            skuId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "SKU 不存在"));
    }

    private SkuStock requireSkuStockForUpdate(long skuId) {
        return jdbcTemplate.query(
            """
            select id, sku_no, sku_name, status, current_stock, available_stock
            from inventory_sku
            where id = ? and deleted_flag = 0
            for update
            """,
            (rs, rowNum) -> new SkuStock(
                rs.getLong("id"),
                rs.getString("sku_no"),
                rs.getString("sku_name"),
                rs.getString("status"),
                rs.getInt("current_stock"),
                rs.getInt("available_stock")),
            skuId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "SKU 不存在"));
    }

    private void requireActiveSupplier(long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from supplier where id = ? and status = 'ACTIVE' and access_status = 'ENABLED' and deleted_flag = 0",
            Integer.class,
            supplierId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "供货商不可用");
        }
    }

    private Optional<StockFlowRow> findStockFlowByIdempotency(String idempotencyKey) {
        return jdbcTemplate.query(
            """
            select id, flow_no, sku_id, biz_type, biz_id, biz_no, order_id, shipment_id, purchase_id,
                   direction, quantity, before_stock, after_stock, operator_user_id, occurred_at, idempotency_key, remark
            from inventory_stock_flow
            where idempotency_key = ?
            """,
            (rs, rowNum) -> mapStockFlow(rs),
            idempotencyKey)
            .stream()
            .findFirst();
    }

    private StockFlowRow mapStockFlow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StockFlowRow(
            rs.getLong("id"),
            rs.getString("flow_no"),
            rs.getLong("sku_id"),
            rs.getString("biz_type"),
            rs.getLong("biz_id"),
            rs.getString("biz_no"),
            nullableLong(rs, "order_id"),
            nullableLong(rs, "shipment_id"),
            nullableLong(rs, "purchase_id"),
            rs.getString("direction"),
            rs.getInt("quantity"),
            rs.getInt("before_stock"),
            rs.getInt("after_stock"),
            nullableLong(rs, "operator_user_id"),
            rs.getObject("occurred_at", LocalDateTime.class),
            rs.getString("idempotency_key"),
            rs.getString("remark"));
    }

    private List<StockFlowResponse> stockFlowsByPurchase(long purchaseId) {
        return jdbcTemplate.query(
            """
            select id, flow_no, sku_id, biz_type, biz_id, biz_no, order_id, shipment_id, purchase_id,
                   direction, quantity, before_stock, after_stock, operator_user_id, occurred_at, idempotency_key, remark
            from inventory_stock_flow
            where purchase_id = ?
            order by id
            """,
            (rs, rowNum) -> mapStockFlow(rs).toResponse(),
            purchaseId);
    }

    private List<Long> stockFlowIdsForPurchase(long purchaseId) {
        return jdbcTemplate.queryForList("select id from inventory_stock_flow where purchase_id = ? order by id", Long.class, purchaseId);
    }

    private long purchaseThresholdCent() {
        return jdbcTemplate.query(
            "select config_value from sys_config where config_group = 'PURCHASE' and config_key = 'PURCHASE_APPROVAL_THRESHOLD_CENT'",
            (rs, rowNum) -> Long.parseLong(rs.getString("config_value")))
            .stream()
            .findFirst()
            .orElse(100000L);
    }

    private Optional<PurchaseRow> findPurchaseByIdempotency(String idempotencyKey) {
        return queryPurchase("idempotency_key = ?", idempotencyKey);
    }

    private Optional<PurchaseRow> findPurchaseByApprovalId(long approvalId) {
        return queryPurchase("approval_id = ?", approvalId);
    }

    private PurchaseRow requirePurchase(long purchaseId) {
        return queryPurchase("id = ?", purchaseId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "采购单不存在"));
    }

    private Optional<PurchaseRow> queryPurchase(String condition, Object value) {
        return jdbcTemplate.query(
            """
            select id, purchase_no, supplier_id, applicant_user_id, total_amount_cent, purchase_status,
                   input_invoice_status, approval_id, threshold_snapshot_cent, expected_arrival_date,
                   supplier_confirm_at, supplier_reject_reason, logistics_company_name, tracking_no,
                   received_at, receiver_user_id, idempotency_key, input_invoice_no,
                   input_invoice_amount_cent, input_invoice_file, created_at
            from purchase_order
            where
            """ + " " + condition,
            (rs, rowNum) -> mapPurchase(rs),
            value)
            .stream()
            .findFirst();
    }

    private PurchaseRow mapPurchase(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PurchaseRow(
            rs.getLong("id"),
            rs.getString("purchase_no"),
            rs.getLong("supplier_id"),
            rs.getLong("applicant_user_id"),
            rs.getLong("total_amount_cent"),
            rs.getString("purchase_status"),
            rs.getString("input_invoice_status"),
            nullableLong(rs, "approval_id"),
            nullableLong(rs, "threshold_snapshot_cent"),
            rs.getObject("expected_arrival_date", LocalDate.class),
            rs.getObject("supplier_confirm_at", LocalDateTime.class),
            rs.getString("supplier_reject_reason"),
            rs.getString("logistics_company_name"),
            rs.getString("tracking_no"),
            rs.getObject("received_at", LocalDateTime.class),
            nullableLong(rs, "receiver_user_id"),
            rs.getString("idempotency_key"),
            rs.getString("input_invoice_no"),
            nullableLong(rs, "input_invoice_amount_cent"),
            rs.getString("input_invoice_file"),
            rs.getObject("created_at", LocalDateTime.class));
    }

    private List<PurchaseItemRow> purchaseItems(long purchaseId) {
        return jdbcTemplate.query(
            """
            select id, purchase_id, purchase_no, line_no, sku_id, sku_no, sku_name,
                   quantity, unit_price_cent, total_amount_cent, received_quantity
            from purchase_order_item
            where purchase_id = ?
            order by line_no
            """,
            (rs, rowNum) -> new PurchaseItemRow(
                rs.getLong("id"),
                rs.getLong("purchase_id"),
                rs.getString("purchase_no"),
                rs.getInt("line_no"),
                rs.getLong("sku_id"),
                rs.getString("sku_no"),
                rs.getString("sku_name"),
                rs.getInt("quantity"),
                rs.getLong("unit_price_cent"),
                rs.getLong("total_amount_cent"),
                rs.getInt("received_quantity")),
            purchaseId);
    }

    private List<ReceiptRow> purchaseReceipts(long purchaseId) {
        return jdbcTemplate.query(
            """
            select id, receipt_no, purchase_id, purchase_no, inbound_batch_no, status,
                   received_at, receiver_user_id, stock_flow_ids, remark
            from purchase_receipt
            where purchase_id = ?
            order by received_at, id
            """,
            (rs, rowNum) -> mapReceipt(rs),
            purchaseId);
    }

    private Optional<ReceiptRow> findReceiptByPurchaseAndBatch(String purchaseNo, String inboundBatchNo) {
        return jdbcTemplate.query(
            """
            select id, receipt_no, purchase_id, purchase_no, inbound_batch_no, status,
                   received_at, receiver_user_id, stock_flow_ids, remark
            from purchase_receipt
            where purchase_no = ? and inbound_batch_no = ?
            """,
            (rs, rowNum) -> mapReceipt(rs),
            purchaseNo,
            inboundBatchNo)
            .stream()
            .findFirst();
    }

    private ReceiptRow mapReceipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReceiptRow(
            rs.getLong("id"),
            rs.getString("receipt_no"),
            rs.getLong("purchase_id"),
            rs.getString("purchase_no"),
            rs.getString("inbound_batch_no"),
            rs.getString("status"),
            rs.getObject("received_at", LocalDateTime.class),
            rs.getLong("receiver_user_id"),
            rs.getString("stock_flow_ids"),
            rs.getString("remark"));
    }

    private PurchaseRow requireSupplierPurchase(SupplierSession supplier, long purchaseId, boolean onlyVisible) {
        PurchaseRow purchase = requirePurchase(purchaseId);
        if (purchase.supplierId() != supplier.supplierId()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该采购单");
        }
        if (onlyVisible && ("APPROVING".equals(purchase.purchaseStatus()) || "APPROVAL_REJECTED".equals(purchase.purchaseStatus()) || "CANCELED".equals(purchase.purchaseStatus()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "采购单当前不可见");
        }
        return purchase;
    }

    private SupplierSession requireSupplierSession(String authorizationHeader) {
        String token = authorizationHeader == null ? "" : authorizationHeader.replace("Bearer", "").trim();
        if (!token.startsWith("S3-SUPPLIER-DEMO-")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商登录态无效");
        }
        String supplierNo = token.substring("S3-SUPPLIER-DEMO-".length());
        return jdbcTemplate.query(
            """
            select id, supplier_no, supplier_name
            from supplier
            where supplier_no = ? and status = 'ACTIVE' and access_status = 'ENABLED' and deleted_flag = 0
            """,
            (rs, rowNum) -> new SupplierSession(
                rs.getLong("id"),
                rs.getString("supplier_no"),
                rs.getString("supplier_name")),
            supplierNo)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "供货商登录态无效"));
    }

    private long countRows(String sql, List<Object> args) {
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return total == null ? 0 : total;
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
        }
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "缺少 Idempotency-Key");
        }
        if (value.trim().length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "幂等键过长");
        }
        return value.trim();
    }

    private long positive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value;
    }

    private int positiveInt(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value;
    }

    private long nonNegative(Long value, String message) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value;
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value.trim();
    }

    private String normalizeChoice(String value, List<String> allowed, String message) {
        String normalized = normalizeText(value);
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return requireText(value, "参数不能为空").toUpperCase();
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "JSON 生成失败");
        }
    }

    private Long nullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }


    private record SkuStock(long skuId, String skuNo, String skuName, String status, int currentStock, int availableStock) {
    }

    private record StockFlowRow(
        long id,
        String flowNo,
        long skuId,
        String bizType,
        long bizId,
        String bizNo,
        Long orderId,
        Long shipmentId,
        Long purchaseId,
        String direction,
        int quantity,
        int beforeStock,
        int afterStock,
        Long operatorUserId,
        LocalDateTime occurredAt,
        String idempotencyKey,
        String remark
    ) {
        StockFlowResponse toResponse() {
            return new StockFlowResponse(id, flowNo, skuId, bizType, bizId, bizNo, orderId, shipmentId, purchaseId, direction, quantity, beforeStock, afterStock, operatorUserId, occurredAt, idempotencyKey, remark);
        }
    }

    private record PreparedPurchaseItem(int lineNo, SkuStock sku, int quantity, long unitPriceCent, long totalAmountCent) {
    }

    private record PurchaseRow(
        long id,
        String purchaseNo,
        long supplierId,
        long applicantUserId,
        long totalAmountCent,
        String purchaseStatus,
        String inputInvoiceStatus,
        Long approvalId,
        Long thresholdSnapshotCent,
        LocalDate expectedArrivalDate,
        LocalDateTime supplierConfirmAt,
        String supplierRejectReason,
        String logisticsCompanyName,
        String trackingNo,
        LocalDateTime receivedAt,
        Long receiverUserId,
        String idempotencyKey,
        String inputInvoiceNo,
        Long inputInvoiceAmountCent,
        String inputInvoiceFile,
        LocalDateTime createdAt
    ) {
        PurchaseSummary toSummary() {
            return new PurchaseSummary(id, purchaseNo, supplierId, totalAmountCent, purchaseStatus, inputInvoiceStatus, approvalId, createdAt);
        }

        PurchaseResponse toResponse(List<PurchaseItemRow> items, List<ReceiptRow> receipts, List<StockFlowResponse> flows) {
            return new PurchaseResponse(
                id,
                purchaseNo,
                supplierId,
                applicantUserId,
                totalAmountCent,
                purchaseStatus,
                inputInvoiceStatus,
                approvalId,
                thresholdSnapshotCent,
                expectedArrivalDate,
                supplierConfirmAt,
                supplierRejectReason,
                logisticsCompanyName,
                trackingNo,
                receivedAt,
                receiverUserId,
                inputInvoiceNo,
                inputInvoiceAmountCent,
                inputInvoiceFile,
                items.stream().map(PurchaseItemRow::toResponse).toList(),
                receipts,
                flows);
        }
    }

    private record PurchaseItemRow(long id, long purchaseId, String purchaseNo, int lineNo, long skuId, String skuNo, String skuName, int quantity, long unitPriceCent, long totalAmountCent, int receivedQuantity) {
        PurchaseItemResponse toResponse() {
            return new PurchaseItemResponse(id, lineNo, skuId, skuNo, skuName, quantity, unitPriceCent, totalAmountCent, receivedQuantity);
        }
    }

    private record SupplierSession(long supplierId, String supplierNo, String supplierName) {
    }
}
