package com.wecombft.application.finance;

import java.math.BigDecimal;
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
import com.wecombft.application.command.finance.TaxRuleSaveCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.interfaces.dto.finance.TaxRulePage;
import com.wecombft.interfaces.dto.finance.TaxRuleResponse;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class TaxRuleApplicationService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final BigDecimal MIN_RATE = BigDecimal.ZERO;
    private static final BigDecimal MAX_RATE = BigDecimal.ONE;

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final AuditLogService auditLogService;

    public TaxRuleApplicationService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditLogService = auditLogService;
    }

    public TaxRulePage adminTaxRules(AdminPrincipal principal, String status, Integer pageNo, Integer pageSize) {
        requireAdmin(principal);
        List<Object> args = new ArrayList<>();
        StringBuilder condition = new StringBuilder("deleted_flag = 0");
        if (status != null && !status.isBlank()) {
            condition.append(" and status = ?");
            args.add(status);
        }
        Integer total = jdbcTemplate.queryForObject(
            "select count(*) from tax_rule where " + condition,
            Integer.class,
            args.toArray());

        int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? DEFAULT_PAGE_NO : Math.max(1, pageNo);
        List<Object> queryArgs = new ArrayList<>(args);
        queryArgs.add(size);
        queryArgs.add((page - 1) * size);

        List<TaxRuleResponse> records = jdbcTemplate.query(
            """
            select id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name, status,
                   description, created_at, updated_at
            from tax_rule
            where %s
            order by created_at desc, id desc
            limit ? offset ?
            """.formatted(condition),
            (rs, rowNum) -> mapTaxRule(rs),
            queryArgs.toArray());

        return new TaxRulePage(records, page, size, total == null ? 0 : total);
    }

    @Transactional
    public TaxRuleResponse saveTaxRule(AdminPrincipal principal, String idempotencyKey, TaxRuleSaveCommand command) {
        requireAdmin(principal);
        if (command == null || command.ruleName() == null || command.ruleName().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "税务规则名称不能为空");
        }
        BigDecimal taxRate = command.taxRate();
        if (taxRate == null || taxRate.compareTo(MIN_RATE) < 0 || taxRate.compareTo(MAX_RATE) > 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "税率必须在 0 到 1 之间");
        }
        String status = command.status() == null || command.status().isBlank() ? "ACTIVE" : command.status();

        if (command.ruleId() == null) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "新增税务规则缺少幂等键");
            }
            if (findByName(command.ruleName()).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "税务规则名称已存在");
            }
            long ruleId = idGenerator.nextId();
            String ruleNo = "TAX" + Long.toString(Math.abs(ruleId % 1_000_000_000L));
            jdbcTemplate.update(
                """
                insert into tax_rule (
                    id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name,
                    status, description, created_by, updated_by
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ruleId,
                ruleNo,
                command.ruleName(),
                command.taxCategory(),
                taxRate,
                command.invoiceItemName(),
                status,
                command.description(),
                principal.userId(),
                principal.userId());
            auditLogService.writeSuccess(principal, "TAX_RULE", "TAX_RULE_CREATE", "TAX_RULE", ruleId, ruleNo, null,
                "{\"rule_name\":\"" + sanitize(command.ruleName()) + "\",\"tax_rate\":\"" + taxRate + "\"}");
            return requireTaxRule(ruleId);
        }

        TaxRuleResponse current = requireTaxRule(command.ruleId());
        Optional<TaxRuleResponse> nameConflict = findByName(command.ruleName());
        if (nameConflict.isPresent() && nameConflict.get().ruleId() != command.ruleId()) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "税务规则名称已被其他记录占用");
        }
        jdbcTemplate.update(
            """
            update tax_rule
            set rule_name = ?, tax_category = ?, tax_rate = ?, invoice_item_name = ?,
                status = ?, description = ?, updated_by = ?, version = version + 1
            where id = ? and deleted_flag = 0
            """,
            command.ruleName(),
            command.taxCategory(),
            taxRate,
            command.invoiceItemName(),
            status,
            command.description(),
            principal.userId(),
            command.ruleId());
        auditLogService.writeSuccess(principal, "TAX_RULE", "TAX_RULE_UPDATE", "TAX_RULE", current.ruleId(), current.ruleNo(), null,
            "{\"rule_name\":\"" + sanitize(command.ruleName()) + "\",\"tax_rate\":\"" + taxRate + "\"}");
        return requireTaxRule(command.ruleId());
    }

    private TaxRuleResponse requireTaxRule(long ruleId) {
        return jdbcTemplate.query(
                """
                select id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name, status,
                       description, created_at, updated_at
                from tax_rule
                where id = ? and deleted_flag = 0
                """,
                (rs, rowNum) -> mapTaxRule(rs),
                ruleId)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "税务规则不存在"));
    }

    private Optional<TaxRuleResponse> findByName(String ruleName) {
        return jdbcTemplate.query(
                """
                select id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name, status,
                       description, created_at, updated_at
                from tax_rule
                where rule_name = ? and deleted_flag = 0
                """,
                (rs, rowNum) -> mapTaxRule(rs),
                ruleName)
            .stream()
            .findFirst();
    }

    private TaxRuleResponse mapTaxRule(ResultSet rs) throws SQLException {
        return new TaxRuleResponse(
            rs.getLong("id"),
            rs.getString("rule_no"),
            rs.getString("rule_name"),
            rs.getString("tax_category"),
            rs.getBigDecimal("tax_rate"),
            rs.getString("invoice_item_name"),
            rs.getString("status"),
            rs.getString("description"),
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
