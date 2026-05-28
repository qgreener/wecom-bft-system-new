package com.wecombft.application.report;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 经营报表聚合接口。
 * - 营收趋势：近 N 日订单数 / 营收（按 paid_at）
 * - 转化漏斗：线索 → 已联系 → 已转化 → 已下单 → 已支付
 * - 课程销量排行：按已支付订单 + 课程快照 group by
 * - 风险预警：当前待开票超期 / 采购无进项票 / 对账差异 / 退款未执行
 */
@Service
public class ReportsApplicationService {

    private final JdbcTemplate jdbcTemplate;

    public ReportsApplicationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RevenueTrendPoint> revenueTrend(int days) {
        int span = Math.max(1, Math.min(days, 30));
        return jdbcTemplate.query(
            """
            select date(paid_at) as day,
                   count(*) as order_count,
                   coalesce(sum(paid_amount_cent), 0) as revenue_cent
            from trade_order
            where payment_status = 'PAID' and paid_at >= ?
            group by date(paid_at)
            order by day
            """,
            (rs, rowNum) -> new RevenueTrendPoint(
                rs.getObject("day", LocalDate.class),
                rs.getLong("order_count"),
                rs.getLong("revenue_cent")),
            java.sql.Date.valueOf(LocalDate.now().minusDays(span - 1)));
    }

    public ConversionFunnel conversionFunnel() {
        long totalLeads = scalar("select count(*) from crm_lead");
        long contactedLeads = scalar("select count(*) from crm_lead where status in ('CONTACTED','CONVERTED')");
        long convertedLeads = scalar("select count(*) from crm_lead where status = 'CONVERTED'");
        long totalOrders = scalar("select count(*) from trade_order");
        long paidOrders = scalar("select count(*) from trade_order where payment_status = 'PAID'");
        return new ConversionFunnel(totalLeads, contactedLeads, convertedLeads, totalOrders, paidOrders);
    }

    public List<SalesRanking> salesRanking() {
        return jdbcTemplate.query(
            """
            select i.course_id,
                   any_value(i.course_snapshot) as course_snapshot,
                   count(distinct o.id) as order_count,
                   coalesce(sum(o.paid_amount_cent), 0) as revenue_cent
            from trade_order_item i
            join trade_order o on o.id = i.order_id
            where o.payment_status = 'PAID' and i.course_id is not null
            group by i.course_id
            order by revenue_cent desc, order_count desc
            limit 10
            """,
            (rs, rowNum) -> new SalesRanking(
                rs.getLong("course_id"),
                extractTitle(rs.getString("course_snapshot")),
                rs.getLong("order_count"),
                rs.getLong("revenue_cent")));
    }

    public RiskAlerts riskAlerts() {
        long pendingInvoiceOverdue = scalar(
            "select count(*) from tax_invoice where status in ('APPLIED','TO_BE_ISSUED') "
                + "and created_at <= date_sub(now(), interval 3 day)");
        long purchaseWithoutInputInvoice = scalar(
            "select count(*) from purchase_order where purchase_status = 'COMPLETED' "
                + "and (input_invoice_status is null or input_invoice_status = 'NOT_INVOICED')");
        long reconciliationDiff = scalar(
            "select count(*) from finance_reconciliation_record where result <> 'MATCHED' and checked_flag = 0");
        long refundFailed = scalar(
            "select count(*) from pay_refund where status in ('FAILED','MANUAL_REQUIRED')");
        return new RiskAlerts(pendingInvoiceOverdue, purchaseWithoutInputInvoice, reconciliationDiff, refundFailed);
    }

    private long scalar(String sql) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }

    private static String extractTitle(String snapshotJson) {
        if (snapshotJson == null) return "-";
        int idx = snapshotJson.indexOf("\"course_title\"");
        if (idx < 0) {
            return snapshotJson.length() > 30 ? snapshotJson.substring(0, 30) + "…" : snapshotJson;
        }
        int colon = snapshotJson.indexOf(':', idx);
        int q1 = snapshotJson.indexOf('"', colon + 1);
        int q2 = snapshotJson.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return "-";
        return snapshotJson.substring(q1 + 1, q2);
    }

    public record RevenueTrendPoint(LocalDate day, long orderCount, long revenueCent) {
    }

    public record ConversionFunnel(long totalLeads, long contactedLeads, long convertedLeads,
                                    long totalOrders, long paidOrders) {
    }

    public record SalesRanking(long courseId, String courseTitle, long orderCount, long revenueCent) {
    }

    public record RiskAlerts(long pendingInvoiceOverdue, long purchaseWithoutInputInvoice,
                              long reconciliationDiff, long refundFailed) {
    }
}
