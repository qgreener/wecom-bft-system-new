package com.wecombft.application.iam;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.interfaces.dto.dashboard.DashboardTodosResponse;
import com.wecombft.interfaces.dto.dashboard.DashboardTodosResponse.TodoSample;
import com.wecombft.shared.web.ApiException;

@Service
public class DashboardApplicationService {

    private static final int SAMPLE_LIMIT = 5;

    static final String PENDING_REFUND_REVIEW = "pending_refund_review";
    static final String PENDING_SHIPMENT = "pending_shipment";
    static final String PENDING_INVOICE_ISSUE = "pending_invoice_issue";
    static final String PENDING_RED_REVERSE = "pending_red_reverse";
    static final String PENDING_RECONCILIATION_DIFF = "pending_reconciliation_diff";
    static final String PENDING_PURCHASE_APPROVAL = "pending_purchase_approval";

    private final JdbcTemplate jdbcTemplate;

    public DashboardApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DashboardTodosResponse todos(AdminPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "缺少管理端登录态");
        }
        List<String> roles = principal.roleCodes();
        Map<String, Long> counts = aggregatedCounts();

        long refundCount = visibleCount(roles, PENDING_REFUND_REVIEW, counts);
        long shipmentCount = visibleCount(roles, PENDING_SHIPMENT, counts);
        long invoiceCount = visibleCount(roles, PENDING_INVOICE_ISSUE, counts);
        long redReverseCount = visibleCount(roles, PENDING_RED_REVERSE, counts);
        long reconCount = visibleCount(roles, PENDING_RECONCILIATION_DIFF, counts);
        long purchaseCount = visibleCount(roles, PENDING_PURCHASE_APPROVAL, counts);

        long todayLeads = countTodayLeads();
        long todayOrders = countTodayOrders();
        long stockWarning = countStockWarning();

        return new DashboardTodosResponse(
            refundCount,
            shipmentCount,
            invoiceCount,
            redReverseCount,
            reconCount,
            purchaseCount,
            todayLeads,
            todayOrders,
            stockWarning,
            refundCount > 0 ? sampleRefundReviews() : List.of(),
            shipmentCount > 0 ? sampleShipments() : List.of(),
            invoiceCount > 0 ? sampleInvoiceIssues() : List.of(),
            redReverseCount > 0 ? sampleRedReverses() : List.of(),
            reconCount > 0 ? sampleReconciliationDiffs() : List.of(),
            purchaseCount > 0 ? samplePurchaseApprovals() : List.of()
        );
    }

    private long countTodayLeads() {
        Long cnt = jdbcTemplate.queryForObject(
            "select count(*) from crm_lead where date(created_at) = curdate()",
            Long.class);
        return cnt == null ? 0L : cnt;
    }

    private long countTodayOrders() {
        Long cnt = jdbcTemplate.queryForObject(
            "select count(*) from trade_order where date(created_at) = curdate()",
            Long.class);
        return cnt == null ? 0L : cnt;
    }

    private long countStockWarning() {
        Long cnt = jdbcTemplate.queryForObject(
            "select count(*) from inventory_sku where available_stock <= safety_stock and deleted_flag = 0 and status = 'ACTIVE'",
            Long.class);
        return cnt == null ? 0L : cnt;
    }

    private Map<String, Long> aggregatedCounts() {
        String sql = """
            select 'pending_refund_review' as pending_type, count(*) as cnt
              from pay_refund where status = 'REVIEWING'
            union all
            select 'pending_shipment', count(*)
              from trade_order where fulfillment_status = 'PENDING_SHIPMENT'
            union all
            select 'pending_invoice_issue', count(*)
              from tax_invoice where status in ('APPLIED','TO_BE_ISSUED')
            union all
            select 'pending_red_reverse', count(*)
              from tax_invoice where status = 'ISSUED' and source_refund_id is not null
            union all
            select 'pending_reconciliation_diff', count(*)
              from finance_reconciliation_record
              where result <> 'MATCHED' and checked_flag = 0
            union all
            select 'pending_purchase_approval', count(*)
              from purchase_order where purchase_status = 'APPROVING'
            """;
        Map<String, Long> map = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getString("pending_type"), rs.getLong("cnt"));
        });
        return map;
    }

    private long visibleCount(List<String> roles, String pendingType, Map<String, Long> counts) {
        if (!canSeeCategory(roles, pendingType)) {
            return 0L;
        }
        Long value = counts.get(pendingType);
        return value == null ? 0L : value;
    }

    static boolean canSeeCategory(List<String> roles, String pendingType) {
        if (roles.contains("SUPER_ADMIN")) {
            return true;
        }
        return switch (pendingType) {
            case PENDING_REFUND_REVIEW -> roles.contains("SERVICE");
            case PENDING_SHIPMENT -> roles.contains("WAREHOUSE") || roles.contains("SERVICE");
            case PENDING_INVOICE_ISSUE, PENDING_RED_REVERSE, PENDING_RECONCILIATION_DIFF -> roles.contains("ACCOUNTING");
            case PENDING_PURCHASE_APPROVAL -> roles.contains("WAREHOUSE");
            default -> false;
        };
    }

    private List<TodoSample> sampleRefundReviews() {
        return jdbcTemplate.query(
            """
            select id, refund_no, status, apply_amount_cent, order_id, created_at
            from pay_refund
            where status = 'REVIEWING'
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> new TodoSample(
                rs.getLong("id"),
                rs.getString("refund_no"),
                "退款待审核",
                rs.getString("status"),
                rs.getLong("apply_amount_cent"),
                rs.getLong("order_id"),
                null,
                toLocalDateTime(rs, "created_at")),
            SAMPLE_LIMIT);
    }

    private List<TodoSample> sampleShipments() {
        return jdbcTemplate.query(
            """
            select id, order_no, fulfillment_status, payable_amount_cent, created_at
            from trade_order
            where fulfillment_status = 'PENDING_SHIPMENT'
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> new TodoSample(
                rs.getLong("id"),
                rs.getString("order_no"),
                "待发货订单",
                rs.getString("fulfillment_status"),
                rs.getLong("payable_amount_cent"),
                rs.getLong("id"),
                rs.getString("order_no"),
                toLocalDateTime(rs, "created_at")),
            SAMPLE_LIMIT);
    }

    private List<TodoSample> sampleInvoiceIssues() {
        return jdbcTemplate.query(
            """
            select id, invoice_apply_no, status, invoice_amount_cent, order_id, created_at
            from tax_invoice
            where status in ('APPLIED','TO_BE_ISSUED')
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> new TodoSample(
                rs.getLong("id"),
                rs.getString("invoice_apply_no"),
                "待开票",
                rs.getString("status"),
                rs.getLong("invoice_amount_cent"),
                rs.getLong("order_id"),
                null,
                toLocalDateTime(rs, "created_at")),
            SAMPLE_LIMIT);
    }

    private List<TodoSample> sampleRedReverses() {
        return jdbcTemplate.query(
            """
            select id, invoice_apply_no, status, invoice_amount_cent, order_id, created_at
            from tax_invoice
            where status = 'ISSUED' and source_refund_id is not null
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> new TodoSample(
                rs.getLong("id"),
                rs.getString("invoice_apply_no"),
                "待红冲",
                rs.getString("status"),
                rs.getLong("invoice_amount_cent"),
                rs.getLong("order_id"),
                null,
                toLocalDateTime(rs, "created_at")),
            SAMPLE_LIMIT);
    }

    private List<TodoSample> sampleReconciliationDiffs() {
        return jdbcTemplate.query(
            """
            select id, batch_no, result, system_amount_cent, order_id, order_no, created_at
            from finance_reconciliation_record
            where result <> 'MATCHED' and checked_flag = 0
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> new TodoSample(
                rs.getLong("id"),
                rs.getString("batch_no"),
                "对账差异",
                rs.getString("result"),
                rs.getLong("system_amount_cent"),
                nullableLong(rs, "order_id"),
                rs.getString("order_no"),
                toLocalDateTime(rs, "created_at")),
            SAMPLE_LIMIT);
    }

    private List<TodoSample> samplePurchaseApprovals() {
        return jdbcTemplate.query(
            """
            select id, purchase_no, purchase_status, total_amount_cent, created_at
            from purchase_order
            where purchase_status = 'APPROVING'
            order by created_at desc, id desc
            limit ?
            """,
            (rs, rowNum) -> new TodoSample(
                rs.getLong("id"),
                rs.getString("purchase_no"),
                "待审批采购",
                rs.getString("purchase_status"),
                rs.getLong("total_amount_cent"),
                null,
                null,
                toLocalDateTime(rs, "created_at")),
            SAMPLE_LIMIT);
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    // Make TodoSample list constructors satisfied for the empty-result case.
    @SuppressWarnings("unused")
    private static List<TodoSample> emptyList() {
        return new ArrayList<>();
    }
}
