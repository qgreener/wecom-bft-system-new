package com.wecombft.application.fulfillment;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecombft.application.audit.AuditLogService;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRecord;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository.CallbackEventCommand;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository.DocumentLinkCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class SupplyChainApplicationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final CallbackEventRepository callbackEventRepository;
    private final OrderDocumentLinkRepository documentLinkRepository;

    public SupplyChainApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        ObjectMapper objectMapper,
        AuditLogService auditLogService,
        CallbackEventRepository callbackEventRepository,
        OrderDocumentLinkRepository documentLinkRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.callbackEventRepository = callbackEventRepository;
        this.documentLinkRepository = documentLinkRepository;
    }

    @Transactional
    public CreationResult<SkuResponse> createSku(AdminPrincipal principal, String idempotencyKey, SkuCommand command) {
        requireAdmin(principal);
        if (command != null && command.skuId() != null) {
            return updateSku(principal, command);
        }
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<SkuRow> existing = findSkuByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new CreationResult<>(existing.get().toResponse(), false);
        }
        String skuName = requireText(command == null ? null : command.skuName(), "SKU 名称不能为空");
        String unit = requireText(command == null ? null : command.unit(), "计量单位不能为空");
        String status = normalizeChoice(defaultString(command.status(), "ACTIVE"), List.of("ACTIVE", "DISABLED"), "SKU 状态非法");
        long skuId = idGenerator.nextId();
        String skuNo = "SKU" + skuId;
        String specAttrs = toJson(command.specAttrs() == null ? Map.of() : command.specAttrs());
        String specAttrsHash = sha256Hex(specAttrs).substring(0, 32);
        Long supplierId = command.defaultSupplierId();
        if (supplierId != null) {
            requireActiveSupplier(supplierId);
        }
        try {
            jdbcTemplate.update(
                """
                insert into inventory_sku (
                    id, sku_no, sku_name, category_code, sku_type, unit, spec_attrs, spec_attrs_hash,
                    default_supplier_id, cost_price_cent, current_stock, locked_stock, available_stock,
                    safety_stock, status, image_url, idempotency_key, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?, ?, ?, ?, ?)
                """,
                skuId,
                skuNo,
                skuName,
                blankToNull(command.categoryCode()),
                normalizeText(defaultString(command.skuType(), "MATERIAL")),
                unit,
                specAttrs,
                specAttrsHash,
                supplierId,
                command.costPriceCent(),
                nonNegative(command.safetyStock(), "安全库存不能为负"),
                status,
                blankToNull(command.imageUrl()),
                key,
                principal.userId(),
                principal.userId());
        } catch (DuplicateKeyException duplicateKeyException) {
            Optional<SkuRow> duplicateExisting = findSkuByIdempotencyKey(key);
            if (duplicateExisting.isPresent()) {
                return new CreationResult<>(duplicateExisting.get().toResponse(), false);
            }
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "SKU 编号或规格已存在");
        }
        auditLogService.writeSuccess(
            principal,
            "INVENTORY",
            "SKU_CREATE",
            "INVENTORY_SKU",
            skuId,
            skuNo,
            null,
            "{\"sku_name\":\"" + jsonSafe(skuName) + "\"}");
        return new CreationResult<>(findSku(skuId).orElseThrow().toResponse(), true);
    }

    private CreationResult<SkuResponse> updateSku(AdminPrincipal principal, SkuCommand command) {
        long skuId = positive(command.skuId(), "SKU 不能为空");
        SkuRow current = findSku(skuId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "SKU 不存在"));
        String skuName = requireText(command.skuName(), "SKU 名称不能为空");
        String unit = requireText(command.unit(), "计量单位不能为空");
        String status = normalizeChoice(defaultString(command.status(), "ACTIVE"), List.of("ACTIVE", "DISABLED"), "SKU 状态非法");
        String specAttrs = toJson(command.specAttrs() == null ? Map.of() : command.specAttrs());
        String specAttrsHash = sha256Hex(specAttrs).substring(0, 32);
        Long supplierId = command.defaultSupplierId();
        if (supplierId != null) {
            requireActiveSupplier(supplierId);
        }
        try {
            jdbcTemplate.update(
                """
                update inventory_sku
                set sku_name = ?,
                    category_code = ?,
                    sku_type = ?,
                    unit = ?,
                    spec_attrs = ?,
                    spec_attrs_hash = ?,
                    default_supplier_id = ?,
                    cost_price_cent = ?,
                    safety_stock = ?,
                    status = ?,
                    image_url = ?,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ? and deleted_flag = 0
                """,
                skuName,
                blankToNull(command.categoryCode()),
                normalizeText(defaultString(command.skuType(), "MATERIAL")),
                unit,
                specAttrs,
                specAttrsHash,
                supplierId,
                command.costPriceCent(),
                nonNegative(command.safetyStock(), "安全库存不能为负"),
                status,
                blankToNull(command.imageUrl()),
                LocalDateTime.now(),
                principal.userId(),
                skuId);
        } catch (DuplicateKeyException duplicateKeyException) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "SKU 编号或规格已存在");
        }
        auditLogService.writeSuccess(
            principal,
            "INVENTORY",
            "SKU_UPDATE",
            "INVENTORY_SKU",
            skuId,
            current.skuNo(),
            null,
            "{\"sku_name\":\"" + jsonSafe(skuName) + "\"}");
        return new CreationResult<>(findSku(skuId).orElseThrow().toResponse(), false);
    }

    public SkuPage skus(String keyword, String categoryCode, String status, Boolean warningOnly, Integer pageNo, Integer pageSize) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where deleted_flag = 0");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (sku_no like ? or sku_name like ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (categoryCode != null && !categoryCode.isBlank()) {
            where.append(" and category_code = ?");
            args.add(categoryCode.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" and status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (Boolean.TRUE.equals(warningOnly)) {
            where.append(" and available_stock <= safety_stock");
        }
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        long total = countRows("select count(*) from inventory_sku" + where, args);
        StringBuilder sql = new StringBuilder(
            """
            select id, sku_no, sku_name, category_code, sku_type, unit, spec_attrs, default_supplier_id,
                   cost_price_cent, current_stock, locked_stock, available_stock, safety_stock, status, image_url
            from inventory_sku
            """)
            .append(where);
        List<Object> queryArgs = new ArrayList<>(args);
        sql.append(" order by created_at desc, id desc limit ? offset ?");
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        List<SkuResponse> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new SkuRow(
            rs.getLong("id"),
            rs.getString("sku_no"),
            rs.getString("sku_name"),
            rs.getString("category_code"),
            rs.getString("sku_type"),
            rs.getString("unit"),
            rs.getString("spec_attrs"),
            nullableLong(rs, "default_supplier_id"),
            nullableLong(rs, "cost_price_cent"),
            rs.getInt("current_stock"),
            rs.getInt("locked_stock"),
            rs.getInt("available_stock"),
            rs.getInt("safety_stock"),
            rs.getString("status"),
            rs.getString("image_url")).toResponse(), queryArgs.toArray());
        return new SkuPage(records, page, size, total);
    }

    @Transactional
    public CreationResult<StockFlowResponse> createStockFlow(AdminPrincipal principal, String idempotencyKey, StockFlowCommand command) {
        requireAdmin(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<StockFlowRow> existing = findStockFlowByIdempotency(key);
        if (existing.isPresent()) {
            return new CreationResult<>(existing.get().toResponse(), false);
        }
        long skuId = positive(command == null ? null : command.skuId(), "SKU 不能为空");
        int quantity = positiveInt(command.quantity(), "库存数量必须大于 0");
        String direction = normalizeChoice(command.direction(), List.of("IN", "OUT"), "库存方向非法");
        SkuStock stock = requireSkuStockForUpdate(skuId);
        int before = stock.availableStock();
        int after = "IN".equals(direction) ? before + quantity : before - quantity;
        if (after < 0) {
            auditLogService.writeFailure(principal, "INVENTORY", "STOCK_FLOW_CREATE_FAILED", "INVENTORY_SKU", skuId, stock.skuNo(), null, "库存不足");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVENTORY_NOT_ENOUGH", "库存不足");
        }
        long flowId = idGenerator.nextId();
        String flowNo = "STF" + flowId;
        String bizType = normalizeText(defaultString(command.bizType(), "MANUAL"));
        String bizNo = defaultString(command.bizNo(), bizType + flowId);
        long bizId = command.bizId() == null ? flowId : command.bizId();
        boolean inserted = insertStockFlow(
            flowId,
            flowNo,
            skuId,
            bizType,
            bizId,
            bizNo,
            command.orderId(),
            command.shipmentId(),
            command.purchaseId(),
            direction,
            quantity,
            before,
            after,
            principal.userId(),
            key,
            command.remark());
        if (!inserted) {
            return new CreationResult<>(findStockFlowByIdempotency(key).orElseThrow().toResponse(), false);
        }
        jdbcTemplate.update(
            """
            update inventory_sku
            set current_stock = ?, available_stock = ?, updated_at = ?, updated_by = ?, version = version + 1
            where id = ?
            """,
            after,
            after,
            LocalDateTime.now(),
            principal.userId(),
            skuId);
        auditLogService.writeSuccess(
            principal,
            "INVENTORY",
            "STOCK_FLOW_CREATE",
            "INVENTORY_STOCK_FLOW",
            flowId,
            flowNo,
            command.orderId(),
            "{\"sku_id\":" + skuId + ",\"direction\":\"" + direction + "\",\"quantity\":" + quantity + "}");
        return new CreationResult<>(findStockFlowByIdempotency(key).orElseThrow().toResponse(), true);
    }

    public StockFlowPage stockFlows(
        Long skuId,
        Long orderId,
        Long shipmentId,
        Long purchaseId,
        String bizType,
        Long bizId,
        LocalDateTime occurredAtStart,
        LocalDateTime occurredAtEnd,
        Integer pageNo,
        Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        appendLongFilter(where, args, "sku_id", skuId);
        appendLongFilter(where, args, "order_id", orderId);
        appendLongFilter(where, args, "shipment_id", shipmentId);
        appendLongFilter(where, args, "purchase_id", purchaseId);
        if (bizType != null && !bizType.isBlank()) {
            where.append(" and biz_type = ?");
            args.add(bizType.trim().toUpperCase());
        }
        appendLongFilter(where, args, "biz_id", bizId);
        if (occurredAtStart != null) {
            where.append(" and occurred_at >= ?");
            args.add(occurredAtStart);
        }
        if (occurredAtEnd != null) {
            where.append(" and occurred_at <= ?");
            args.add(occurredAtEnd);
        }
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        long total = countRows("select count(*) from inventory_stock_flow" + where, args);
        StringBuilder sql = new StringBuilder(
            """
            select id, flow_no, sku_id, biz_type, biz_id, biz_no, order_id, shipment_id, purchase_id,
                   direction, quantity, before_stock, after_stock, operator_user_id, occurred_at, idempotency_key, remark
            from inventory_stock_flow
            """)
            .append(where);
        List<Object> queryArgs = new ArrayList<>(args);
        sql.append(" order by occurred_at desc, id desc limit ? offset ?");
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        return new StockFlowPage(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapStockFlow(rs).toResponse(), queryArgs.toArray()), page, size, total);
    }

    public ShipmentPage shipments(String status, String orderNo, String trackingNo, Boolean exceptionFlag, Integer pageNo, Integer pageSize) {
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (status != null && !status.isBlank()) {
            where.append(" and status = ?");
            args.add(status.trim().toUpperCase());
        }
        if (orderNo != null && !orderNo.isBlank()) {
            where.append(" and order_no like ?");
            args.add("%" + orderNo.trim() + "%");
        }
        if (trackingNo != null && !trackingNo.isBlank()) {
            where.append(" and tracking_no = ?");
            args.add(trackingNo.trim());
        }
        if (exceptionFlag != null) {
            where.append(" and exception_flag = ?");
            args.add(exceptionFlag ? 1 : 0);
        }
        int size = pageSize == null ? 20 : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? 1 : Math.max(1, pageNo);
        long total = countRows("select count(*) from fulfillment_shipment" + where, args);
        StringBuilder sql = new StringBuilder(
            """
            select id, shipment_no, order_id, order_no, student_id, status, receiver_snapshot,
                   logistics_company_code, logistics_company_name, tracking_no, waybill_file,
                   shipped_at, shipper_user_id, signed_at, exception_flag, exception_reason, created_at
            from fulfillment_shipment
            """)
            .append(where);
        List<Object> queryArgs = new ArrayList<>(args);
        sql.append(" order by created_at desc, id desc limit ? offset ?");
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);
        List<ShipmentListItem> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapShipment(rs).toListItem(), queryArgs.toArray());
        return new ShipmentPage(records, page, size, total);
    }

    public ShipmentDetailResponse shipmentDetail(long shipmentId) {
        ShipmentRow shipment = requireShipment(shipmentId);
        return new ShipmentDetailResponse(
            shipment.toActionResponse(stockFlowIdsForShipment(shipmentId)),
            shipmentItems(shipmentId).stream()
                .map(item -> new ShipmentItemResponse(item.id(), item.skuId(), item.skuNo(), item.skuName(), item.lineType(), item.quantity(), item.stockFlowId()))
                .toList(),
            tracesByShipment(shipmentId),
            stockFlowsByShipment(shipmentId),
            documentLinkRepository.findByOrderId(shipment.orderId()).stream().map(this::toDocumentLinkResponse).toList());
    }

    @Transactional
    public ShipmentActionResponse ship(AdminPrincipal principal, long shipmentId, String idempotencyKey, ShipCommand command) {
        requireAdmin(principal);
        requireIdempotencyKey(idempotencyKey);
        ShipmentRow shipment = requireShipment(shipmentId);
        OrderRow order = requireOrder(shipment.orderId());
        if ("SHIPPED".equals(shipment.status()) || "SIGNED".equals(shipment.status())) {
            return requireShipment(shipmentId).toActionResponse(stockFlowIdsForShipment(shipmentId));
        }
        validateShipmentCanShip(principal, shipment, order);
        if ("CREATE_WAYBILL_FAILED".equalsIgnoreCase(command == null ? null : command.mockScenario())) {
            auditLogService.writeFailure(principal, "FULFILLMENT", "SHIPMENT_WAYBILL_FAILED", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), shipment.orderId(), "模拟获取运单失败");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FULFILLMENT_WAYBILL_FAILED", "物流 Mock 获取运单失败");
        }
        String trackingNo = requireText(command == null ? null : command.trackingNo(), "物流单号不能为空");
        String logisticsCompanyName = requireText(command.logisticsCompanyName(), "物流公司不能为空");
        String logisticsCompanyCode = blankToNull(command.logisticsCompanyCode());
        if ("EXTERNAL_CREATED_INTERNAL_FAILED".equalsIgnoreCase(command.mockScenario())) {
            LocalDateTime failedAt = LocalDateTime.now();
            jdbcTemplate.update(
                """
                update fulfillment_shipment
                set logistics_company_code = ?,
                    logistics_company_name = ?,
                    tracking_no = ?,
                    waybill_file = ?,
                    exception_flag = 1,
                    exception_reason = ?,
                    updated_at = ?,
                    updated_by = ?,
                    version = version + 1
                where id = ? and status = 'PENDING_SHIPMENT'
                """,
                logisticsCompanyCode,
                logisticsCompanyName,
                trackingNo,
                blankToNull(command.waybillFile()),
                "外部已出单内部保存失败，待补偿",
                failedAt,
                principal.userId(),
                shipmentId);
            upsertOrderDocument(order, "SHIPMENT", shipment.id(), shipment.shipmentNo(), "EXCEPTION", null, "SHIPMENT_EXCEPTION", "fulfillment_shipment", "外部已出单内部保存失败，待补偿", principal.userId());
            auditLogService.writeFailure(
                principal,
                "FULFILLMENT",
                "SHIPMENT_EXTERNAL_FAILED",
                "FULFILLMENT_SHIPMENT",
                shipment.id(),
                shipment.shipmentNo(),
                order.id(),
                "外部已出单内部保存失败，待补偿");
            return requireShipment(shipmentId).toActionResponse(stockFlowIdsForShipment(shipmentId));
        }
        List<ShipmentItemRow> items = shipmentItems(shipmentId);
        ensureStockEnough(principal, shipment, items);

        LocalDateTime shippedAt = LocalDateTime.now();
        int shipmentUpdated = jdbcTemplate.update(
            """
            update fulfillment_shipment
            set status = 'SHIPPED',
                logistics_company_code = ?,
                logistics_company_name = ?,
                tracking_no = ?,
                waybill_file = ?,
                shipped_at = ?,
                shipper_user_id = ?,
                exception_flag = ?,
                exception_reason = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status = 'PENDING_SHIPMENT'
            """,
            logisticsCompanyCode,
            logisticsCompanyName,
            trackingNo,
            blankToNull(command.waybillFile()),
            shippedAt,
            principal.userId(),
            "WAYBILL_FAILED".equalsIgnoreCase(command.mockScenario()) ? 1 : 0,
            "WAYBILL_FAILED".equalsIgnoreCase(command.mockScenario()) ? "面单或云打印失败，待补偿" : null,
            shippedAt,
            principal.userId(),
            shipmentId);
        if (shipmentUpdated == 0) {
            return requireShipment(shipmentId).toActionResponse(stockFlowIdsForShipment(shipmentId));
        }

        List<Long> flowIds = new ArrayList<>();
        for (ShipmentItemRow item : items) {
            StockFlowRow flow = outboundForShipment(principal, shipment, item);
            flowIds.add(flow.id());
            jdbcTemplate.update("update fulfillment_shipment_item set stock_flow_id = ?, updated_at = ?, updated_by = ? where id = ?",
                flow.id(),
                LocalDateTime.now(),
                principal.userId(),
                item.id());
            upsertOrderDocument(order, "STOCK_FLOW", flow.id(), flow.flowNo(), "OUT", null, "SHIPMENT_OUTBOUND", "inventory_stock_flow", "发货出库流水", principal.userId());
        }
        jdbcTemplate.update(
            """
            update trade_order
            set fulfillment_status = 'SHIPPED',
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and fulfillment_status = 'PENDING_SHIPMENT'
            """,
            shippedAt,
            principal.userId(),
            order.id());
        upsertOrderDocument(order, "SHIPMENT", shipment.id(), shipment.shipmentNo(), "SHIPPED", null, "SHIPMENT_OUTBOUND", "fulfillment_shipment", "确认发货", principal.userId());
        auditLogService.writeSuccess(
            principal,
            "FULFILLMENT",
            "SHIPMENT_SHIP",
            "FULFILLMENT_SHIPMENT",
            shipment.id(),
            shipment.shipmentNo(),
            order.id(),
            "{\"tracking_no\":\"" + jsonSafe(trackingNo) + "\",\"stock_flow_count\":" + flowIds.size() + "}");
        return requireShipment(shipmentId).toActionResponse(flowIds);
    }

    @Transactional
    public ShipmentActionResponse sign(AdminPrincipal principal, long shipmentId, SignCommand command) {
        requireAdmin(principal);
        ShipmentRow shipment = requireShipment(shipmentId);
        if ("SIGNED".equals(shipment.status())) {
            return shipment.toActionResponse(stockFlowIdsForShipment(shipmentId));
        }
        if (!"SHIPPED".equals(shipment.status())) {
            auditLogService.writeFailure(principal, "FULFILLMENT", "SHIPMENT_SIGN_STATE_CONFLICT", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), shipment.orderId(), "未发货不可签收");
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "未发货不可签收");
        }
        LocalDateTime signedAt = command == null || command.signedAt() == null ? LocalDateTime.now() : command.signedAt();
        markShipmentSigned(shipment, signedAt, principal.userId());
        auditLogService.writeSuccess(principal, "FULFILLMENT", "SHIPMENT_SIGN", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), shipment.orderId(), "{\"status\":\"SIGNED\"}");
        return requireShipment(shipmentId).toActionResponse(stockFlowIdsForShipment(shipmentId));
    }

    @Transactional
    public LogisticsCallbackResponse handleLogisticsCallback(LogisticsTraceCommand command) {
        validateLogisticsCallback(command);
        Optional<ShipmentRow> shipmentOptional = findShipmentByTrackingOrNo(command.trackingNo(), command.shipmentNo());
        ShipmentRow shipment = shipmentOptional.orElse(null);
        String idempotencyKey = command.trackingNo().trim() + ":" + command.logisticsNodeTime() + ":" + command.nodeStatus().trim().toUpperCase();
        String rawSnapshot = toJson(command.rawSnapshot() == null ? Map.of("event_no", command.eventNo()) : command.rawSnapshot());
        CallbackEventRecord callbackEvent = callbackEventRepository.recordReceived(new CallbackEventCommand(
            idGenerator.nextId(),
            command.eventNo().trim(),
            "LOGISTICS_MOCK",
            "LOGISTICS_TRACE",
            idempotencyKey,
            shipment == null ? null : shipment.orderId(),
            "FULFILLMENT_SHIPMENT",
            shipment == null ? null : shipment.id(),
            shipment == null ? command.shipmentNo() : shipment.shipmentNo(),
            rawSnapshot));
        if (!"PENDING".equals(callbackEvent.processingStatus())) {
            ShipmentRow latest = shipment == null ? null : requireShipment(shipment.id());
            return new LogisticsCallbackResponse(callbackEvent.processingStatus(), latest == null ? null : latest.id(), latest == null ? null : latest.orderId(), command.trackingNo(), latest == null ? null : latest.status(), callbackEvent.failureReason());
        }
        if (shipment == null) {
            callbackEventRepository.markFailed(callbackEvent.id(), "未找到物流单号对应发货单");
            return new LogisticsCallbackResponse("FAILED", null, null, command.trackingNo(), null, "未找到物流单号对应发货单");
        }

        Long traceId = insertTraceIfAbsent(shipment, command, rawSnapshot);
        if (traceId != null) {
            upsertOrderDocument(
                requireOrder(shipment.orderId()),
                "LOGISTICS_TRACE",
                traceId,
                command.trackingNo(),
                command.nodeStatus().trim().toUpperCase(),
                null,
                "LOGISTICS_CALLBACK",
                "logistics_trace",
                "物流轨迹回调",
                null);
        }
        ShipmentRow latest = requireShipment(shipment.id());
        if (Boolean.TRUE.equals(command.signedFlag()) && "SHIPPED".equals(latest.status())) {
            markShipmentSigned(latest, command.logisticsNodeTime(), null);
            latest = requireShipment(shipment.id());
        }
        callbackEventRepository.markProcessed(callbackEvent.id());
        auditLogService.writeSystemSuccess(
            "FULFILLMENT",
            "LOGISTICS_CALLBACK",
            "FULFILLMENT_SHIPMENT",
            latest.id(),
            latest.shipmentNo(),
            latest.orderId(),
            "{\"node_status\":\"" + jsonSafe(command.nodeStatus()) + "\",\"status\":\"" + latest.status() + "\"}");
        return new LogisticsCallbackResponse("PROCESSED", latest.id(), latest.orderId(), latest.trackingNo(), latest.status(), null);
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
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "采购单当前状态不可审批回写");
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

    private void validateShipmentCanShip(AdminPrincipal principal, ShipmentRow shipment, OrderRow order) {
        if (!"PAID".equals(order.paymentStatus()) || !"PENDING_SHIPMENT".equals(order.fulfillmentStatus()) || "REFUNDED".equals(order.refundStatus())) {
            auditLogService.writeFailure(principal, "FULFILLMENT", "SHIPMENT_STATE_CONFLICT", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), order.id(), "订单状态不允许发货");
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "订单状态不允许发货");
        }
        if (!"PENDING_SHIPMENT".equals(shipment.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "发货单当前状态不允许发货");
        }
    }

    private void ensureStockEnough(AdminPrincipal principal, ShipmentRow shipment, List<ShipmentItemRow> items) {
        Map<Long, Integer> required = new LinkedHashMap<>();
        for (ShipmentItemRow item : items) {
            required.merge(item.skuId(), item.quantity(), Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : required.entrySet()) {
            SkuStock stock = requireSkuStock(entry.getKey());
            if (stock.availableStock() < entry.getValue()) {
                jdbcTemplate.update(
                    """
                    update fulfillment_shipment
                    set exception_flag = 1,
                        exception_reason = ?,
                        updated_at = ?
                    where id = ?
                    """,
                    "库存不足，SKU=" + stock.skuNo(),
                    LocalDateTime.now(),
                    shipment.id());
                auditLogService.writeFailure(principal, "FULFILLMENT", "SHIPMENT_STOCK_NOT_ENOUGH", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), shipment.orderId(), "库存不足");
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVENTORY_NOT_ENOUGH", "库存不足：" + stock.skuName());
            }
        }
    }

    private StockFlowRow outboundForShipment(AdminPrincipal principal, ShipmentRow shipment, ShipmentItemRow item) {
        String key = "SHIPMENT:" + shipment.shipmentNo() + ":" + item.id();
        Optional<StockFlowRow> existing = findStockFlowByIdempotency(key);
        if (existing.isPresent()) {
            return existing.get();
        }
        SkuStock stock = requireSkuStockForUpdate(item.skuId());
        int before = stock.availableStock();
        int after = before - item.quantity();
        if (after < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVENTORY_NOT_ENOUGH", "库存不足：" + stock.skuName());
        }
        long flowId = idGenerator.nextId();
        String flowNo = "STF" + flowId;
        boolean inserted = insertStockFlow(
            flowId,
            flowNo,
            item.skuId(),
            "SHIPMENT",
            shipment.id(),
            shipment.shipmentNo(),
            shipment.orderId(),
            shipment.id(),
            null,
            "OUT",
            item.quantity(),
            before,
            after,
            principal.userId(),
            key,
            "发货出库");
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

    private Long insertTraceIfAbsent(ShipmentRow shipment, LogisticsTraceCommand command, String rawSnapshot) {
        Optional<Long> existing = findTraceId(command.trackingNo(), command.logisticsNodeTime(), command.nodeStatus());
        if (existing.isPresent()) {
            return null;
        }
        long traceId = idGenerator.nextId();
        try {
            jdbcTemplate.update(
                """
                insert into logistics_trace (
                    id, shipment_id, order_id, tracking_no, logistics_node_time, node_status, node_desc, raw_snapshot
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                traceId,
                shipment.id(),
                shipment.orderId(),
                command.trackingNo().trim(),
                command.logisticsNodeTime(),
                normalizeText(command.nodeStatus()),
                blankToNull(command.nodeDesc()),
                rawSnapshot);
            return traceId;
        } catch (DuplicateKeyException duplicateKeyException) {
            return null;
        }
    }

    private void markShipmentSigned(ShipmentRow shipment, LocalDateTime signedAt, Long operatorUserId) {
        jdbcTemplate.update(
            """
            update fulfillment_shipment
            set status = 'SIGNED',
                signed_at = ?,
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and status <> 'SIGNED'
            """,
            signedAt,
            LocalDateTime.now(),
            operatorUserId,
            shipment.id());
        jdbcTemplate.update(
            """
            update trade_order
            set fulfillment_status = 'SIGNED',
                updated_at = ?,
                updated_by = ?,
                version = version + 1
            where id = ? and fulfillment_status <> 'SIGNED'
            """,
            LocalDateTime.now(),
            operatorUserId,
            shipment.orderId());
        upsertOrderDocument(requireOrder(shipment.orderId()), "SHIPMENT", shipment.id(), shipment.shipmentNo(), "SIGNED", null, "LOGISTICS_SIGNED", "fulfillment_shipment", "物流签收", operatorUserId);
    }

    private void upsertOrderDocument(
        OrderRow order,
        String documentType,
        long documentId,
        String documentNo,
        String documentStatus,
        Long amountCent,
        String relationType,
        String sourceTable,
        String remark,
        Long operatorUserId
    ) {
        documentLinkRepository.upsert(new DocumentLinkCommand(
            idGenerator.nextId(),
            order.id(),
            order.orderNo(),
            documentType,
            documentId,
            documentNo,
            documentStatus,
            amountCent,
            relationType,
            LocalDateTime.now(),
            sourceTable,
            remark,
            operatorUserId));
    }

    private PurchaseReceiptResponse receiptResponse(ReceiptRow receipt) {
        PurchaseRow purchase = requirePurchase(receipt.purchaseId());
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

    private void validateLogisticsCallback(LogisticsTraceCommand command) {
        if (command == null
            || command.eventNo() == null || command.eventNo().isBlank()
            || command.trackingNo() == null || command.trackingNo().isBlank()
            || command.logisticsNodeTime() == null
            || command.nodeStatus() == null || command.nodeStatus().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "物流回调关键字段不能为空");
        }
    }

    private Optional<SkuRow> findSku(long skuId) {
        return jdbcTemplate.query(
            """
            select id, sku_no, sku_name, category_code, sku_type, unit, spec_attrs, default_supplier_id,
                   cost_price_cent, current_stock, locked_stock, available_stock, safety_stock, status, image_url
            from inventory_sku
            where id = ? and deleted_flag = 0
            """,
            (rs, rowNum) -> new SkuRow(
                rs.getLong("id"),
                rs.getString("sku_no"),
                rs.getString("sku_name"),
                rs.getString("category_code"),
                rs.getString("sku_type"),
                rs.getString("unit"),
                rs.getString("spec_attrs"),
                nullableLong(rs, "default_supplier_id"),
                nullableLong(rs, "cost_price_cent"),
                rs.getInt("current_stock"),
                rs.getInt("locked_stock"),
                rs.getInt("available_stock"),
                rs.getInt("safety_stock"),
                rs.getString("status"),
                rs.getString("image_url")),
            skuId)
            .stream()
            .findFirst();
    }

    private Optional<SkuRow> findSkuByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query(
            """
            select id, sku_no, sku_name, category_code, sku_type, unit, spec_attrs, default_supplier_id,
                   cost_price_cent, current_stock, locked_stock, available_stock, safety_stock, status, image_url
            from inventory_sku
            where idempotency_key = ? and deleted_flag = 0
            """,
            (rs, rowNum) -> new SkuRow(
                rs.getLong("id"),
                rs.getString("sku_no"),
                rs.getString("sku_name"),
                rs.getString("category_code"),
                rs.getString("sku_type"),
                rs.getString("unit"),
                rs.getString("spec_attrs"),
                nullableLong(rs, "default_supplier_id"),
                nullableLong(rs, "cost_price_cent"),
                rs.getInt("current_stock"),
                rs.getInt("locked_stock"),
                rs.getInt("available_stock"),
                rs.getInt("safety_stock"),
                rs.getString("status"),
                rs.getString("image_url")),
            idempotencyKey)
            .stream()
            .findFirst();
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

    private ShipmentRow requireShipment(long shipmentId) {
        return jdbcTemplate.query(
            """
            select id, shipment_no, order_id, order_no, student_id, status, receiver_snapshot,
                   logistics_company_code, logistics_company_name, tracking_no, waybill_file,
                   shipped_at, shipper_user_id, signed_at, exception_flag, exception_reason, created_at
            from fulfillment_shipment
            where id = ?
            """,
            (rs, rowNum) -> mapShipment(rs),
            shipmentId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "发货单不存在"));
    }

    private ShipmentRow mapShipment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ShipmentRow(
            rs.getLong("id"),
            rs.getString("shipment_no"),
            rs.getLong("order_id"),
            rs.getString("order_no"),
            rs.getLong("student_id"),
            rs.getString("status"),
            rs.getString("receiver_snapshot"),
            rs.getString("logistics_company_code"),
            rs.getString("logistics_company_name"),
            rs.getString("tracking_no"),
            rs.getString("waybill_file"),
            rs.getObject("shipped_at", LocalDateTime.class),
            nullableLong(rs, "shipper_user_id"),
            rs.getObject("signed_at", LocalDateTime.class),
            rs.getBoolean("exception_flag"),
            rs.getString("exception_reason"),
            rs.getObject("created_at", LocalDateTime.class));
    }

    private List<ShipmentItemRow> shipmentItems(long shipmentId) {
        return jdbcTemplate.query(
            """
            select id, shipment_id, shipment_no, order_id, order_item_id, sku_id, sku_no, sku_name,
                   line_type, quantity, stock_flow_id
            from fulfillment_shipment_item
            where shipment_id = ?
            order by id
            """,
            (rs, rowNum) -> new ShipmentItemRow(
                rs.getLong("id"),
                rs.getLong("shipment_id"),
                rs.getString("shipment_no"),
                rs.getLong("order_id"),
                nullableLong(rs, "order_item_id"),
                rs.getLong("sku_id"),
                rs.getString("sku_no"),
                rs.getString("sku_name"),
                rs.getString("line_type"),
                rs.getInt("quantity"),
                nullableLong(rs, "stock_flow_id")),
            shipmentId);
    }

    private List<LogisticsTraceResponse> tracesByShipment(long shipmentId) {
        return jdbcTemplate.query(
            """
            select id, shipment_id, order_id, tracking_no, logistics_node_time, node_status, node_desc
            from logistics_trace
            where shipment_id = ?
            order by logistics_node_time, id
            """,
            (rs, rowNum) -> new LogisticsTraceResponse(
                rs.getLong("id"),
                rs.getLong("shipment_id"),
                rs.getLong("order_id"),
                rs.getString("tracking_no"),
                rs.getObject("logistics_node_time", LocalDateTime.class),
                rs.getString("node_status"),
                rs.getString("node_desc")),
            shipmentId);
    }

    private List<StockFlowResponse> stockFlowsByShipment(long shipmentId) {
        return jdbcTemplate.query(
            """
            select id, flow_no, sku_id, biz_type, biz_id, biz_no, order_id, shipment_id, purchase_id,
                   direction, quantity, before_stock, after_stock, operator_user_id, occurred_at, idempotency_key, remark
            from inventory_stock_flow
            where shipment_id = ?
            order by id
            """,
            (rs, rowNum) -> mapStockFlow(rs).toResponse(),
            shipmentId);
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

    private List<Long> stockFlowIdsForShipment(long shipmentId) {
        return jdbcTemplate.queryForList("select id from inventory_stock_flow where shipment_id = ? order by id", Long.class, shipmentId);
    }

    private List<Long> stockFlowIdsForPurchase(long purchaseId) {
        return jdbcTemplate.queryForList("select id from inventory_stock_flow where purchase_id = ? order by id", Long.class, purchaseId);
    }

    private Optional<ShipmentRow> findShipmentByTrackingOrNo(String trackingNo, String shipmentNo) {
        return jdbcTemplate.query(
            """
            select id, shipment_no, order_id, order_no, student_id, status, receiver_snapshot,
                   logistics_company_code, logistics_company_name, tracking_no, waybill_file,
                   shipped_at, shipper_user_id, signed_at, exception_flag, exception_reason, created_at
            from fulfillment_shipment
            where tracking_no = ? or shipment_no = ?
            order by id
            limit 1
            """,
            (rs, rowNum) -> mapShipment(rs),
            trackingNo,
            shipmentNo)
            .stream()
            .findFirst();
    }

    private Optional<Long> findTraceId(String trackingNo, LocalDateTime nodeTime, String nodeStatus) {
        return jdbcTemplate.query(
            """
            select id
            from logistics_trace
            where tracking_no = ? and logistics_node_time = ? and node_status = ?
            """,
            (rs, rowNum) -> rs.getLong("id"),
            trackingNo,
            nodeTime,
            normalizeText(nodeStatus))
            .stream()
            .findFirst();
    }

    private OrderRow requireOrder(long orderId) {
        return jdbcTemplate.query(
            """
            select id, order_no, payment_status, fulfillment_status, refund_status
            from trade_order
            where id = ?
            """,
            (rs, rowNum) -> new OrderRow(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getString("payment_status"),
                rs.getString("fulfillment_status"),
                rs.getString("refund_status")),
            orderId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "订单不存在"));
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

    private DocumentLinkResponse toDocumentLinkResponse(com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRecord record) {
        return new DocumentLinkResponse(
            record.id(),
            record.documentType(),
            record.documentId(),
            record.documentNo(),
            record.documentStatus(),
            record.amountCent(),
            record.relationType(),
            record.occurredAt(),
            record.sourceTable(),
            record.remark());
    }

    private void appendLongFilter(StringBuilder sql, List<Object> args, String columnName, Long value) {
        if (value != null) {
            sql.append(" and ").append(columnName).append(" = ?");
            args.add(value);
        }
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

    private int nonNegative(Integer value, String message) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
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

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return fromJson(node.asText());
            }
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of("raw", json);
        }
    }

    private String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private Long nullableLong(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record CreationResult<T>(T response, boolean created) {
    }

    public record SkuCommand(
        Long skuId,
        String skuName,
        String categoryCode,
        String skuType,
        String unit,
        Map<String, Object> specAttrs,
        Long defaultSupplierId,
        Long costPriceCent,
        Integer safetyStock,
        String status,
        String imageUrl
    ) {
    }

    public record StockFlowCommand(
        Long skuId,
        String direction,
        Integer quantity,
        String bizType,
        Long bizId,
        String bizNo,
        Long orderId,
        Long shipmentId,
        Long purchaseId,
        String remark
    ) {
    }

    public record ShipCommand(String logisticsCompanyCode, String logisticsCompanyName, String trackingNo, String waybillFile, String remark, String mockScenario) {
    }

    public record SignCommand(LocalDateTime signedAt, String remark) {
    }

    public record LogisticsTraceCommand(
        String eventNo,
        String shipmentNo,
        String trackingNo,
        LocalDateTime logisticsNodeTime,
        String nodeStatus,
        String nodeDesc,
        Boolean signedFlag,
        Map<String, Object> rawSnapshot
    ) {
    }

    public record PurchaseCreateCommand(Long supplierId, List<PurchaseItemCommand> purchaseItems, String submitReason, LocalDate expectedArrivalDate) {
    }

    public record PurchaseItemCommand(Long skuId, Integer quantity, Long unitPriceCent) {
    }

    public record ApprovalActionCommand(String action, @JsonAlias("approval_comment") String comment) {
    }

    public record SupplierConfirmCommand(LocalDate expectedArrivalDate, String remark) {
    }

    public record SupplierRejectCommand(String supplierRejectReason) {
    }

    public record SupplierLogisticsCommand(String logisticsCompanyName, String trackingNo, String remark) {
    }

    public record PurchaseReceiveCommand(String inboundBatchNo, List<ReceivedItemCommand> receivedItems, String remark) {
    }

    public record ReceivedItemCommand(Long skuId, Integer receivedQuantity) {
    }

    public record PurchaseInputInvoiceCommand(String inputInvoiceNo, Long inputInvoiceAmountCent, String inputInvoiceFile) {
    }

    public record SkuPage(List<SkuResponse> records, int pageNo, int pageSize, long total) {
    }

    public record SkuResponse(
        long skuId,
        String skuNo,
        String skuName,
        String categoryCode,
        String skuType,
        String unit,
        Map<String, Object> specAttrs,
        Long defaultSupplierId,
        Long costPriceCent,
        int currentStock,
        int lockedStock,
        int availableStock,
        int safetyStock,
        String status,
        String imageUrl
    ) {
    }

    public record StockFlowPage(List<StockFlowResponse> records, int pageNo, int pageSize, long total) {
    }

    public record StockFlowResponse(
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
        int beforeStock,
        int afterStock,
        Long operatorUserId,
        LocalDateTime occurredAt,
        String idempotencyKey,
        String remark
    ) {
    }

    public record ShipmentPage(List<ShipmentListItem> records, int pageNo, int pageSize, long total) {
    }

    public record ShipmentListItem(
        long shipmentId,
        String shipmentNo,
        long orderId,
        String orderNo,
        long studentId,
        String status,
        String logisticsCompanyName,
        String trackingNo,
        LocalDateTime shippedAt,
        LocalDateTime signedAt,
        boolean exceptionFlag,
        String exceptionReason
    ) {
    }

    public record ShipmentActionResponse(
        long shipmentId,
        String shipmentNo,
        long orderId,
        String status,
        String logisticsCompanyName,
        String trackingNo,
        LocalDateTime shippedAt,
        LocalDateTime signedAt,
        List<Long> stockFlowIds,
        boolean exceptionFlag,
        String exceptionReason
    ) {
    }

    public record ShipmentDetailResponse(
        ShipmentActionResponse shipment,
        List<ShipmentItemResponse> items,
        List<LogisticsTraceResponse> traces,
        List<StockFlowResponse> stockFlows,
        List<DocumentLinkResponse> documentLinks
    ) {
    }

    public record ShipmentItemResponse(long itemId, long skuId, String skuNo, String skuName, String lineType, int quantity, Long stockFlowId) {
    }

    public record LogisticsTraceResponse(long traceId, long shipmentId, long orderId, String trackingNo, LocalDateTime logisticsNodeTime, String nodeStatus, String nodeDesc) {
    }

    public record LogisticsCallbackResponse(String processingStatus, Long shipmentId, Long orderId, String trackingNo, String status, String failureReason) {
    }

    public record PurchasePage(List<PurchaseSummary> records, int pageNo, int pageSize, long total) {
    }

    public record PurchaseSummary(long purchaseId, String purchaseNo, long supplierId, long totalAmountCent, String purchaseStatus, String inputInvoiceStatus, Long approvalId, LocalDateTime createdAt) {
    }

    public record PurchaseResponse(
        long purchaseId,
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
        String inputInvoiceNo,
        Long inputInvoiceAmountCent,
        String inputInvoiceFile,
        List<PurchaseItemResponse> purchaseItems,
        List<ReceiptRow> receipts,
        List<StockFlowResponse> stockFlows
    ) {
    }

    public record PurchaseItemResponse(long itemId, int lineNo, long skuId, String skuNo, String skuName, int quantity, long unitPriceCent, long totalAmountCent, int receivedQuantity) {
    }

    public record PurchaseReceiptResponse(long receiptId, String receiptNo, long purchaseId, String purchaseNo, String status, LocalDateTime receivedAt, List<Long> stockFlowIds, String purchaseStatus) {
    }

    public record DocumentLinkResponse(
        long documentLinkId,
        String documentType,
        long documentId,
        String documentNo,
        String documentStatus,
        Long amountCent,
        String relationType,
        LocalDateTime occurredAt,
        String sourceTable,
        String remark
    ) {
    }

    private record SkuRow(
        long skuId,
        String skuNo,
        String skuName,
        String categoryCode,
        String skuType,
        String unit,
        String specAttrs,
        Long defaultSupplierId,
        Long costPriceCent,
        int currentStock,
        int lockedStock,
        int availableStock,
        int safetyStock,
        String status,
        String imageUrl
    ) {
        SkuResponse toResponse() {
            return new SkuResponse(skuId, skuNo, skuName, categoryCode, skuType, unit, fromSpecAttrs(specAttrs), defaultSupplierId, costPriceCent, currentStock, lockedStock, availableStock, safetyStock, status, imageUrl);
        }

        private Map<String, Object> fromSpecAttrs(String json) {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(json);
                if (node.isTextual()) {
                    return fromSpecAttrs(node.asText());
                }
                return mapper.convertValue(node, MAP_TYPE);
            } catch (JsonProcessingException exception) {
                return Map.of("raw", json);
            }
        }
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

    private record ShipmentRow(
        long id,
        String shipmentNo,
        long orderId,
        String orderNo,
        long studentId,
        String status,
        String receiverSnapshot,
        String logisticsCompanyCode,
        String logisticsCompanyName,
        String trackingNo,
        String waybillFile,
        LocalDateTime shippedAt,
        Long shipperUserId,
        LocalDateTime signedAt,
        boolean exceptionFlag,
        String exceptionReason,
        LocalDateTime createdAt
    ) {
        ShipmentListItem toListItem() {
            return new ShipmentListItem(id, shipmentNo, orderId, orderNo, studentId, status, logisticsCompanyName, trackingNo, shippedAt, signedAt, exceptionFlag, exceptionReason);
        }

        ShipmentActionResponse toActionResponse(List<Long> stockFlowIds) {
            return new ShipmentActionResponse(id, shipmentNo, orderId, status, logisticsCompanyName, trackingNo, shippedAt, signedAt, stockFlowIds, exceptionFlag, exceptionReason);
        }
    }

    private record ShipmentItemRow(long id, long shipmentId, String shipmentNo, long orderId, Long orderItemId, long skuId, String skuNo, String skuName, String lineType, int quantity, Long stockFlowId) {
    }

    private record OrderRow(long id, String orderNo, String paymentStatus, String fulfillmentStatus, String refundStatus) {
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

    public record ReceiptRow(long id, String receiptNo, long purchaseId, String purchaseNo, String inboundBatchNo, String status, LocalDateTime receivedAt, long receiverUserId, String stockFlowIds, String remark) {
    }

    private record SupplierSession(long supplierId, String supplierNo, String supplierName) {
    }
}
