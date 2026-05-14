package com.wecombft.application.inventory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.command.inventory.SkuCommand;
import com.wecombft.application.command.inventory.StockFlowCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.interfaces.dto.inventory.SkuPage;
import com.wecombft.interfaces.dto.inventory.SkuResponse;
import com.wecombft.interfaces.dto.inventory.StockFlowPage;
import com.wecombft.interfaces.dto.inventory.StockFlowResponse;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class InventoryApplicationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public InventoryApplicationService(
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
    public CreationResult<SkuResponse> createSku(AdminPrincipal principal, String idempotencyKey, SkuCommand command) {
        requireAdmin(principal);
        if (command != null && command.skuId() != null) {
            return updateSku(principal, command);
        }
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<SkuRow> existing = findSkuByIdempotencyKey(key);
        if (existing.isPresent()) {
            return new CreationResult<>(toSkuResponse(existing.get()), false);
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
                return new CreationResult<>(toSkuResponse(duplicateExisting.get()), false);
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
        return new CreationResult<>(toSkuResponse(findSku(skuId).orElseThrow()), true);
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
        List<SkuResponse> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toSkuResponse(new SkuRow(
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
            rs.getString("image_url"))), queryArgs.toArray());
        return new SkuPage(records, page, size, total);
    }

    @Transactional
    public CreationResult<StockFlowResponse> createStockFlow(AdminPrincipal principal, String idempotencyKey, StockFlowCommand command) {
        requireAdmin(principal);
        String key = requireIdempotencyKey(idempotencyKey);
        Optional<StockFlowRow> existing = findStockFlowByIdempotency(key);
        if (existing.isPresent()) {
            return new CreationResult<>(toStockFlowResponse(existing.get()), false);
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
            return new CreationResult<>(toStockFlowResponse(findStockFlowByIdempotency(key).orElseThrow()), false);
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
        return new CreationResult<>(toStockFlowResponse(findStockFlowByIdempotency(key).orElseThrow()), true);
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
        return new StockFlowPage(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> toStockFlowResponse(mapStockFlow(rs)), queryArgs.toArray()), page, size, total);
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
        return new CreationResult<>(toSkuResponse(findSku(skuId).orElseThrow()), false);
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

    private void requireActiveSupplier(long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from supplier where id = ? and status = 'ACTIVE' and deleted_flag = 0",
            Integer.class,
            supplierId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "默认供货商不存在或未启用");
        }
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

    private SkuResponse toSkuResponse(SkuRow row) {
        return new SkuResponse(
            row.skuId(),
            row.skuNo(),
            row.skuName(),
            row.categoryCode(),
            row.skuType(),
            row.unit(),
            fromJson(row.specAttrs()),
            row.defaultSupplierId(),
            row.costPriceCent(),
            row.currentStock(),
            row.lockedStock(),
            row.availableStock(),
            row.safetyStock(),
            row.status(),
            row.imageUrl());
    }

    private StockFlowResponse toStockFlowResponse(StockFlowRow row) {
        return new StockFlowResponse(
            row.id(),
            row.flowNo(),
            row.skuId(),
            row.bizType(),
            row.bizId(),
            row.bizNo(),
            row.orderId(),
            row.shipmentId(),
            row.purchaseId(),
            row.direction(),
            row.quantity(),
            row.beforeStock(),
            row.afterStock(),
            row.operatorUserId(),
            row.occurredAt(),
            row.idempotencyKey(),
            row.remark());
    }

    public record CreationResult<T>(T response, boolean created) {
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
    }
}
