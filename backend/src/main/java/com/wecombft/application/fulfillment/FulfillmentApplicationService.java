package com.wecombft.application.fulfillment;

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
import com.wecombft.application.command.fulfillment.LogisticsTraceCommand;
import com.wecombft.application.command.fulfillment.ShipCommand;
import com.wecombft.application.command.fulfillment.SignCommand;
import com.wecombft.interfaces.dto.fulfillment.DocumentLinkResponse;
import com.wecombft.interfaces.dto.fulfillment.LogisticsCallbackResponse;
import com.wecombft.interfaces.dto.fulfillment.LogisticsTraceResponse;
import com.wecombft.interfaces.dto.fulfillment.ShipmentActionResponse;
import com.wecombft.interfaces.dto.fulfillment.ShipmentDetailResponse;
import com.wecombft.interfaces.dto.fulfillment.ShipmentItemResponse;
import com.wecombft.interfaces.dto.fulfillment.ShipmentListItem;
import com.wecombft.interfaces.dto.fulfillment.ShipmentPage;
import com.wecombft.interfaces.dto.fulfillment.StockFlowResponse;
import com.wecombft.domain.model.fulfillment.ShipmentStateSnapshot;
import com.wecombft.domain.service.fulfillment.FulfillmentStateDomainService;
import com.wecombft.domain.service.fulfillment.FulfillmentStateException;
import com.wecombft.infrastructure.integration.logistics.LogisticsAdapter;
import com.wecombft.infrastructure.integration.logistics.LogisticsAdapterException;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRecord;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository.CallbackEventCommand;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository.DocumentLinkCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class FulfillmentApplicationService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final CallbackEventRepository callbackEventRepository;
    private final OrderDocumentLinkRepository documentLinkRepository;
    private final LogisticsAdapter logisticsAdapter;
    private final FulfillmentStateDomainService fulfillmentStateDomainService = new FulfillmentStateDomainService();

    public FulfillmentApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        ObjectMapper objectMapper,
        AuditLogService auditLogService,
        CallbackEventRepository callbackEventRepository,
        OrderDocumentLinkRepository documentLinkRepository,
        LogisticsAdapter logisticsAdapter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.callbackEventRepository = callbackEventRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.logisticsAdapter = logisticsAdapter;
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
        LogisticsAdapter.WaybillResult waybill;
        try {
            waybill = logisticsAdapter.createWaybill(new LogisticsAdapter.WaybillRequest(
                shipment.id(),
                shipment.shipmentNo(),
                order.id(),
                order.orderNo(),
                command == null ? null : command.logisticsCompanyCode(),
                command == null ? null : command.logisticsCompanyName(),
                command == null ? null : command.trackingNo(),
                command == null ? null : command.waybillFile(),
                command == null ? null : command.mockScenario()));
        } catch (LogisticsAdapterException exception) {
            auditLogService.writeFailure(principal, "FULFILLMENT", "SHIPMENT_WAYBILL_FAILED", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), shipment.orderId(), exception.getMessage());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, exception.errorCode(), exception.getMessage());
        }
        String trackingNo = requireText(waybill.trackingNo(), "物流单号不能为空");
        String logisticsCompanyName = requireText(waybill.logisticsCompanyName(), "物流公司不能为空");
        String logisticsCompanyCode = blankToNull(waybill.logisticsCompanyCode());
        String waybillFile = blankToNull(waybill.waybillFile());
        if (waybill.externalCreatedInternalFailed()) {
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
                waybillFile,
                waybill.exceptionReason(),
                failedAt,
                principal.userId(),
                shipmentId);
            upsertOrderDocument(order, "SHIPMENT", shipment.id(), shipment.shipmentNo(), "EXCEPTION", null, "SHIPMENT_EXCEPTION", "fulfillment_shipment", waybill.exceptionReason(), principal.userId());
            auditLogService.writeFailure(
                principal,
                "FULFILLMENT",
                "SHIPMENT_EXTERNAL_FAILED",
                "FULFILLMENT_SHIPMENT",
                shipment.id(),
                shipment.shipmentNo(),
                order.id(),
                waybill.exceptionReason());
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
            waybillFile,
            shippedAt,
            principal.userId(),
            waybill.waybillFailed() ? 1 : 0,
            waybill.waybillFailed() ? waybill.exceptionReason() : null,
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

    private void validateShipmentCanShip(AdminPrincipal principal, ShipmentRow shipment, OrderRow order) {
        try {
            fulfillmentStateDomainService.ensureCanShip(new ShipmentStateSnapshot(
                order.paymentStatus(),
                order.fulfillmentStatus(),
                order.refundStatus(),
                shipment.status()));
        } catch (FulfillmentStateException exception) {
            if (exception.orderStateViolation()) {
                auditLogService.writeFailure(principal, "FULFILLMENT", "SHIPMENT_STATE_CONFLICT", "FULFILLMENT_SHIPMENT", shipment.id(), shipment.shipmentNo(), order.id(), exception.getMessage());
            }
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", exception.getMessage());
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

    private void validateLogisticsCallback(LogisticsTraceCommand command) {
        if (command == null
            || command.eventNo() == null || command.eventNo().isBlank()
            || command.trackingNo() == null || command.trackingNo().isBlank()
            || command.logisticsNodeTime() == null
            || command.nodeStatus() == null || command.nodeStatus().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "物流回调关键字段不能为空");
        }
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

    private List<Long> stockFlowIdsForShipment(long shipmentId) {
        return jdbcTemplate.queryForList("select id from inventory_stock_flow where shipment_id = ? order by id", Long.class, shipmentId);
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

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value.trim();
    }

    private String normalizeText(String value) {
        return requireText(value, "参数不能为空").toUpperCase();
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

}
