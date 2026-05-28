package com.wecombft.application.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.wecombft.shared.web.ApiException;

/**
 * 代账工作台聚合查询：
 * - 收入明细：按月份的已支付订单
 * - 退款明细：按月份的已退款 / 待人工退款记录
 * - 采购支出：按月份的已完成采购单
 * - 待开票：当前所有未开票/待开具发票申请
 * - 开票汇总：按月份已开具发票
 * - 销项税：按月份发票税额（按发票自带的 tax_amount_cent 汇总）
 * - 税负概览：销项税 / 营收 / 进项税 / 应纳税额 / 税负率
 */
@Service
public class AccountingWorkbenchService {

    private final JdbcTemplate jdbcTemplate;

    public AccountingWorkbenchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IncomeRow> income(String relatedMonth) {
        MonthRange range = parseMonth(relatedMonth);
        return jdbcTemplate.query(
            """
            select o.id as order_id, o.order_no, o.student_id, o.paid_amount_cent, o.paid_at
            from trade_order o
            where o.payment_status = 'PAID' and o.paid_at >= ? and o.paid_at < ?
            order by o.paid_at desc, o.id desc
            limit 200
            """,
            (rs, rowNum) -> new IncomeRow(
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getLong("student_id"),
                rs.getLong("paid_amount_cent"),
                rs.getObject("paid_at", LocalDateTime.class)),
            range.from(),
            range.to());
    }

    public List<RefundRow> refunds(String relatedMonth) {
        MonthRange range = parseMonth(relatedMonth);
        return jdbcTemplate.query(
            """
            select r.id as refund_id, r.refund_no, r.order_id, r.apply_amount_cent,
                   coalesce(r.approved_amount_cent, r.apply_amount_cent) as approved_amount_cent,
                   r.status, r.refund_reason, r.refunded_at, r.created_at
            from pay_refund r
            where r.created_at >= ? and r.created_at < ?
            order by r.created_at desc, r.id desc
            limit 200
            """,
            (rs, rowNum) -> new RefundRow(
                rs.getLong("refund_id"),
                rs.getString("refund_no"),
                rs.getLong("order_id"),
                rs.getLong("apply_amount_cent"),
                rs.getLong("approved_amount_cent"),
                rs.getString("status"),
                rs.getString("refund_reason"),
                rs.getObject("refunded_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)),
            range.from(),
            range.to());
    }

    public List<PurchaseRow> purchases(String relatedMonth) {
        MonthRange range = parseMonth(relatedMonth);
        return jdbcTemplate.query(
            """
            select p.id as purchase_id, p.purchase_no, p.supplier_id, p.total_amount_cent,
                   p.purchase_status, p.input_invoice_status, p.received_at, p.created_at
            from purchase_order p
            where p.created_at >= ? and p.created_at < ?
            order by p.created_at desc, p.id desc
            limit 200
            """,
            (rs, rowNum) -> new PurchaseRow(
                rs.getLong("purchase_id"),
                rs.getString("purchase_no"),
                (Long) rs.getObject("supplier_id"),
                rs.getLong("total_amount_cent"),
                rs.getString("purchase_status"),
                rs.getString("input_invoice_status"),
                rs.getObject("received_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class)),
            range.from(),
            range.to());
    }

    public List<InvoicePendingRow> pendingInvoices() {
        return jdbcTemplate.query(
            """
            select id as invoice_id, invoice_apply_no, order_id, order_no, student_id,
                   title_type, title_name, tax_no, email, invoice_amount_cent, status, created_at
            from tax_invoice
            where status in ('APPLIED','TO_BE_ISSUED')
            order by created_at desc, id desc
            limit 200
            """,
            (rs, rowNum) -> new InvoicePendingRow(
                rs.getLong("invoice_id"),
                rs.getString("invoice_apply_no"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getLong("student_id"),
                rs.getString("title_type"),
                rs.getString("title_name"),
                rs.getString("tax_no"),
                rs.getString("email"),
                rs.getLong("invoice_amount_cent"),
                rs.getString("status"),
                rs.getObject("created_at", LocalDateTime.class)));
    }

    public List<InvoiceIssuedRow> issuedInvoices(String relatedMonth) {
        MonthRange range = parseMonth(relatedMonth);
        return jdbcTemplate.query(
            """
            select id as invoice_id, invoice_apply_no, invoice_no, order_id, order_no,
                   title_type, title_name, tax_no, email,
                   invoice_amount_cent, tax_amount_cent, issued_at
            from tax_invoice
            where status = 'ISSUED' and issued_at >= ? and issued_at < ?
            order by issued_at desc, id desc
            limit 200
            """,
            (rs, rowNum) -> new InvoiceIssuedRow(
                rs.getLong("invoice_id"),
                rs.getString("invoice_apply_no"),
                rs.getString("invoice_no"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getString("title_type"),
                rs.getString("title_name"),
                rs.getString("tax_no"),
                rs.getString("email"),
                rs.getLong("invoice_amount_cent"),
                (Long) rs.getObject("tax_amount_cent"),
                rs.getObject("issued_at", LocalDateTime.class)),
            range.from(),
            range.to());
    }

    public TaxBurdenOverview taxBurden(String relatedMonth) {
        MonthRange range = parseMonth(relatedMonth);
        Long revenueCent = jdbcTemplate.queryForObject(
            "select coalesce(sum(paid_amount_cent), 0) from trade_order "
                + "where payment_status = 'PAID' and paid_at >= ? and paid_at < ?",
            Long.class, range.from(), range.to());
        Long outputTaxCent = jdbcTemplate.queryForObject(
            "select coalesce(sum(tax_amount_cent), 0) from tax_invoice "
                + "where status = 'ISSUED' and issued_at >= ? and issued_at < ?",
            Long.class, range.from(), range.to());
        // 进项税：用采购单中已开票的金额 × 6%（演示估算口径）
        Long inputAmount = jdbcTemplate.queryForObject(
            "select coalesce(sum(total_amount_cent), 0) from purchase_order "
                + "where input_invoice_status = 'INVOICED' "
                + "and (completed_at >= ? and completed_at < ?)",
            Long.class, range.from(), range.to());
        long revenue = revenueCent == null ? 0 : revenueCent;
        long output = outputTaxCent == null ? 0 : outputTaxCent;
        long input = inputAmount == null ? 0 : Math.round((inputAmount.longValue()) * 0.06);
        long payable = Math.max(0, output - input);
        BigDecimal burdenRate = revenue == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(payable).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(revenue), 4, RoundingMode.HALF_UP);
        return new TaxBurdenOverview(
            range.month(),
            revenue,
            output,
            input,
            payable,
            burdenRate);
    }

    private MonthRange parseMonth(String input) {
        try {
            YearMonth ym = (input == null || input.isBlank())
                ? YearMonth.now()
                : YearMonth.parse(input);
            LocalDateTime from = ym.atDay(1).atStartOfDay();
            LocalDateTime to = ym.plusMonths(1).atDay(1).atStartOfDay();
            return new MonthRange(ym.toString(), from, to);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "月份格式应为 YYYY-MM");
        }
    }

    private record MonthRange(String month, LocalDateTime from, LocalDateTime to) {
    }

    public record IncomeRow(long orderId, String orderNo, long studentId, long paidAmountCent, LocalDateTime paidAt) {
    }

    public record RefundRow(long refundId, String refundNo, long orderId, long applyAmountCent,
                             long approvedAmountCent, String status, String refundReason,
                             LocalDateTime refundedAt, LocalDateTime createdAt) {
    }

    public record PurchaseRow(long purchaseId, String purchaseNo, Long supplierId, long totalAmountCent,
                               String purchaseStatus, String inputInvoiceStatus,
                               LocalDateTime completedAt, LocalDateTime createdAt) {
    }

    public record InvoicePendingRow(long invoiceId, String invoiceApplyNo, long orderId, String orderNo,
                                     long studentId, String titleType, String titleName, String taxNo,
                                     String email, long invoiceAmountCent, String status,
                                     LocalDateTime createdAt) {
    }

    public record InvoiceIssuedRow(long invoiceId, String invoiceApplyNo, String invoiceNo,
                                    long orderId, String orderNo, String titleType, String titleName,
                                    String taxNo, String email, long invoiceAmountCent,
                                    Long taxAmountCent, LocalDateTime issuedAt) {
    }

    public record TaxBurdenOverview(String relatedMonth, long revenueCent, long outputTaxCent,
                                     long inputTaxCent, long payableTaxCent, BigDecimal burdenRatePercent) {
    }
}
