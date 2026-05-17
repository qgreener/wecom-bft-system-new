package com.wecombft.application.purchase;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.command.purchase.SupplierSaveCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.interfaces.dto.supplier.SupplierPage;
import com.wecombft.interfaces.dto.supplier.SupplierResponse;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class SupplierApplicationService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public SupplierApplicationService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    public SupplierPage adminSuppliers(
        AdminPrincipal principal,
        String keyword,
        String accessStatus,
        String status,
        Integer pageNo,
        Integer pageSize
    ) {
        requireAdmin(principal);
        List<Object> args = new ArrayList<>();
        StringBuilder condition = new StringBuilder("deleted_flag = 0");
        if (keyword != null && !keyword.isBlank()) {
            condition.append(" and (supplier_no like ? or supplier_name like ? or short_name like ? or contact_name like ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (accessStatus != null && !accessStatus.isBlank()) {
            condition.append(" and access_status = ?");
            args.add(accessStatus);
        }
        if (status != null && !status.isBlank()) {
            condition.append(" and status = ?");
            args.add(status);
        }
        Integer total = jdbcTemplate.queryForObject(
            "select count(*) from supplier where " + condition,
            Integer.class,
            args.toArray());

        int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? DEFAULT_PAGE_NO : Math.max(1, pageNo);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);

        List<SupplierResponse> records = jdbcTemplate.query(
            """
            select id, supplier_no, supplier_name, short_name, contact_name, contact_mobile,
                   contact_email, tax_no, address, settlement_method, access_status, status,
                   created_at, updated_at
            from supplier
            where %s
            order by created_at desc, id desc
            limit ? offset ?
            """.formatted(condition),
            (rs, rowNum) -> mapSupplier(rs),
            queryArgs.toArray());

        return new SupplierPage(records, page, size, total == null ? 0 : total);
    }

    @Transactional
    public SupplierResponse saveSupplier(AdminPrincipal principal, String idempotencyKey, SupplierSaveCommand command) {
        requireAdmin(principal);
        if (command == null || command.supplierName() == null || command.supplierName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "供货商名称不能为空");
        }
        if (command.contactName() == null || command.contactName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "联系人不能为空");
        }
        if (command.contactMobile() == null || command.contactMobile().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "联系电话不能为空");
        }
        String accessStatus = command.accessStatus() == null || command.accessStatus().isBlank() ? "ACTIVE" : command.accessStatus();
        String status = command.status() == null || command.status().isBlank() ? "ACTIVE" : command.status();

        if (command.supplierId() == null) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "新增供货商缺少幂等键");
            }
            Optional<SupplierResponse> existing = findByName(command.supplierName());
            if (existing.isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "供货商名称已存在");
            }
            long supplierId = idGenerator.nextId();
            String supplierNo = "SUP" + Long.toString(Math.abs(supplierId % 1_000_000_000L));
            jdbcTemplate.update(
                """
                insert into supplier (
                    id, supplier_no, supplier_name, short_name, contact_name, contact_mobile,
                    contact_email, tax_no, address, settlement_method, access_status, status,
                    created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                supplierId,
                supplierNo,
                command.supplierName(),
                command.shortName(),
                command.contactName(),
                command.contactMobile(),
                command.contactEmail(),
                command.taxNo(),
                command.address(),
                command.settlementMethod(),
                accessStatus,
                status,
                principal.userId(),
                principal.userId());
            auditLogService.writeSuccess(principal, "SUPPLIER", "SUPPLIER_CREATE", "SUPPLIER", supplierId, supplierNo, null,
                "{\"supplier_name\":\"" + sanitize(command.supplierName()) + "\"}");
            return requireSupplier(supplierId);
        }

        SupplierResponse current = requireSupplier(command.supplierId());
        Optional<SupplierResponse> nameConflict = findByName(command.supplierName());
        if (nameConflict.isPresent() && nameConflict.get().supplierId() != command.supplierId()) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "供货商名称已被其他记录占用");
        }
        jdbcTemplate.update(
            """
            update supplier
            set supplier_name = ?, short_name = ?, contact_name = ?, contact_mobile = ?,
                contact_email = ?, tax_no = ?, address = ?, settlement_method = ?,
                access_status = ?, status = ?, updated_by = ?, version = version + 1
            where id = ? and deleted_flag = 0
            """,
            command.supplierName(),
            command.shortName(),
            command.contactName(),
            command.contactMobile(),
            command.contactEmail(),
            command.taxNo(),
            command.address(),
            command.settlementMethod(),
            accessStatus,
            status,
            principal.userId(),
            command.supplierId());
        auditLogService.writeSuccess(principal, "SUPPLIER", "SUPPLIER_UPDATE", "SUPPLIER", current.supplierId(), current.supplierNo(), null,
            "{\"supplier_name\":\"" + sanitize(command.supplierName()) + "\"}");
        return requireSupplier(command.supplierId());
    }

    private SupplierResponse requireSupplier(long supplierId) {
        return jdbcTemplate.query(
                """
                select id, supplier_no, supplier_name, short_name, contact_name, contact_mobile,
                       contact_email, tax_no, address, settlement_method, access_status, status,
                       created_at, updated_at
                from supplier
                where id = ? and deleted_flag = 0
                """,
                (rs, rowNum) -> mapSupplier(rs),
                supplierId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "供货商不存在"));
    }

    private Optional<SupplierResponse> findByName(String supplierName) {
        return jdbcTemplate.query(
                """
                select id, supplier_no, supplier_name, short_name, contact_name, contact_mobile,
                       contact_email, tax_no, address, settlement_method, access_status, status,
                       created_at, updated_at
                from supplier
                where supplier_name = ? and deleted_flag = 0
                """,
                (rs, rowNum) -> mapSupplier(rs),
                supplierName)
            .stream()
            .findFirst();
    }

    private SupplierResponse mapSupplier(ResultSet rs) throws SQLException {
        return new SupplierResponse(
            rs.getLong("id"),
            rs.getString("supplier_no"),
            rs.getString("supplier_name"),
            rs.getString("short_name"),
            rs.getString("contact_name"),
            rs.getString("contact_mobile"),
            rs.getString("contact_email"),
            rs.getString("tax_no"),
            rs.getString("address"),
            rs.getString("settlement_method"),
            rs.getString("access_status"),
            rs.getString("status"),
            toLocalDateTime(rs, "created_at"),
            toLocalDateTime(rs, "updated_at"));
    }

    private void requireAdmin(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
