package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class S8PcAdminCrudControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(8_000_000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_list_students_with_role_specific_mobile_mask() throws Exception {
        String superToken = adminLogin("DEMO_ADMIN");
        String serviceToken = adminLogin("DEMO_SERVICE");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");

        long studentId = seedStudent("S8_STUDENT_MASK", "13912345678");

        // SUPER_ADMIN sees plain mobile
        mockMvc.perform(get("/api/admin/students")
                .header("Authorization", "Bearer " + superToken)
                .param("keyword", "S8_STUDENT_MASK"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].mobile").value("13912345678"));

        // SERVICE sees tail-4 mask
        mockMvc.perform(get("/api/admin/students/{student_id}", studentId)
                .header("Authorization", "Bearer " + serviceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mobile").value("139****5678"));

        // ACCOUNTING sees full mask
        mockMvc.perform(get("/api/admin/students/{student_id}", studentId)
                .header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mobile").value("***********"));
    }

    @Test
    void should_return_404_for_unknown_student_detail() throws Exception {
        String superToken = adminLogin("DEMO_ADMIN");
        mockMvc.perform(get("/api/admin/students/{student_id}", 999_999_999L)
                .header("Authorization", "Bearer " + superToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_reject_student_list_without_permission() throws Exception {
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        mockMvc.perform(get("/api/admin/students")
                .header("Authorization", "Bearer " + warehouseToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_list_payments_with_filter_and_pagination() throws Exception {
        String serviceToken = adminLogin("DEMO_SERVICE");
        seedPayment("S8_PAY_SUCCESS_1", "S8_MO_1", 12300L, "SUCCESS", LocalDateTime.now().minusDays(1));
        seedPayment("S8_PAY_FAILED_1", "S8_MO_2", 45600L, "FAILED", LocalDateTime.now().minusDays(2));

        mockMvc.perform(get("/api/admin/payments")
                .header("Authorization", "Bearer " + serviceToken)
                .param("payment_result", "SUCCESS")
                .param("page_size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page_no").value(1))
            .andExpect(jsonPath("$.data.page_size").value(10))
            .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)));

        // ACCOUNTING also has payment:read
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        mockMvc.perform(get("/api/admin/payments")
                .header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk());

        // TEACHER lacks payment:read
        String teacherToken = adminLogin("DEMO_TEACHER");
        mockMvc.perform(get("/api/admin/payments")
                .header("Authorization", "Bearer " + teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_list_admin_entitlements_filtered_by_status_and_student() throws Exception {
        String superToken = adminLogin("DEMO_ADMIN");
        long studentId = seedStudent("S8_ENT_STUDENT", "13800001111");
        seedEntitlement(studentId, "ACTIVE");
        seedEntitlement(studentId, "FROZEN");

        mockMvc.perform(get("/api/admin/entitlements")
                .header("Authorization", "Bearer " + superToken)
                .param("student_id", String.valueOf(studentId))
                .param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"));
    }

    @Test
    void should_crud_suppliers_with_uniqueness_check() throws Exception {
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");

        String firstResp = mockMvc.perform(post("/api/admin/suppliers")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s8-supplier-create-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "supplier_name": "S8 Test Supplier A",
                      "contact_name": "联系人 A",
                      "contact_mobile": "13900008001",
                      "tax_no": "91330100S8SUP000A"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.supplier_name").value("S8 Test Supplier A"))
            .andReturn().getResponse().getContentAsString();
        long supplierId = extractLong(firstResp, "supplier_id");

        // Duplicate name should conflict
        mockMvc.perform(post("/api/admin/suppliers")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s8-supplier-create-dup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"supplier_name":"S8 Test Supplier A","contact_name":"重复","contact_mobile":"13900008003"}
                    """))
            .andExpect(status().isConflict());

        // Update (no Idempotency-Key required for updates per impl, but ok)
        mockMvc.perform(post("/api/admin/suppliers")
                .header("Authorization", "Bearer " + warehouseToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"supplier_id":%d,"supplier_name":"S8 Test Supplier A","contact_name":"联系人 A 更新","contact_mobile":"13900008002"}
                    """.formatted(supplierId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contact_name").value("联系人 A 更新"));

        // List visible to WAREHOUSE and ACCOUNTING
        mockMvc.perform(get("/api/admin/suppliers")
                .header("Authorization", "Bearer " + warehouseToken)
                .param("keyword", "S8 Test Supplier"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)));

        // SERVICE lacks supplier:read
        String serviceToken = adminLogin("DEMO_SERVICE");
        mockMvc.perform(get("/api/admin/suppliers")
                .header("Authorization", "Bearer " + serviceToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_crud_tax_rules_with_rate_validation() throws Exception {
        String accountingToken = adminLogin("DEMO_ACCOUNTING");

        // Out-of-range tax rate rejected
        mockMvc.perform(post("/api/admin/tax-rules")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-tax-rate-invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rule_name":"S8 Invalid Rate","tax_category":"VAT_GENERAL","tax_rate":1.5,"invoice_item_name":"咨询服务"}
                    """))
            .andExpect(status().isUnprocessableEntity());

        // Valid creation
        String resp = mockMvc.perform(post("/api/admin/tax-rules")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-tax-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rule_name":"S8 Standard VAT","tax_category":"VAT_GENERAL","tax_rate":0.06,"invoice_item_name":"咨询服务"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.tax_rate").value(0.06))
            .andReturn().getResponse().getContentAsString();
        long ruleId = extractLong(resp, "rule_id");
        assertThat(ruleId).isGreaterThan(0);

        // Name conflict
        mockMvc.perform(post("/api/admin/tax-rules")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-tax-create-dup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"rule_name":"S8 Standard VAT","tax_category":"VAT_GENERAL","tax_rate":0.13,"invoice_item_name":"咨询服务"}
                    """))
            .andExpect(status().isConflict());

        // Lacking permission
        String serviceToken = adminLogin("DEMO_SERVICE");
        mockMvc.perform(get("/api/admin/tax-rules")
                .header("Authorization", "Bearer " + serviceToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_return_dashboard_todos_filtered_by_role() throws Exception {
        // Seed one pending refund and one pending purchase approval
        seedRefundReview();
        seedPurchaseApproving();

        // SUPER_ADMIN sees everything
        String superToken = adminLogin("DEMO_ADMIN");
        mockMvc.perform(get("/api/admin/dashboard/todos")
                .header("Authorization", "Bearer " + superToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pending_refund_review_count", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.pending_purchase_approval_count", greaterThanOrEqualTo(1)));

        // SERVICE sees refunds but zero for purchase approval
        String serviceToken = adminLogin("DEMO_SERVICE");
        mockMvc.perform(get("/api/admin/dashboard/todos")
                .header("Authorization", "Bearer " + serviceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.pending_refund_review_count", greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.data.pending_purchase_approval_count").value(0));

        // TEACHER blocked entirely (no permitted permissions)
        String teacherToken = adminLogin("DEMO_TEACHER");
        mockMvc.perform(get("/api/admin/dashboard/todos")
                .header("Authorization", "Bearer " + teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_retry_failed_refund_and_reject_other_statuses() throws Exception {
        String serviceToken = adminLogin("DEMO_SERVICE");
        long refundId = seedFailedRefund();

        mockMvc.perform(post("/api/admin/refunds/{refund_id}/retry", refundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s8-refund-retry-orig")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retry_mode\":\"ORIGINAL\",\"remark\":\"重试原路退款\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));

        // Retry MANUAL on a fresh failed refund
        long refundId2 = seedFailedRefund();
        mockMvc.perform(post("/api/admin/refunds/{refund_id}/retry", refundId2)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s8-refund-retry-manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retry_mode\":\"MANUAL_REQUIRED\",\"remark\":\"转人工\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MANUAL_REQUIRED"));

        // Reject retry on already PROCESSING refund (state conflict idempotent return)
        mockMvc.perform(post("/api/admin/refunds/{refund_id}/retry", refundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s8-refund-retry-dup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"retry_mode\":\"ORIGINAL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    void should_check_reconciliation_record_idempotently() throws Exception {
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long recordId = seedReconciliationRecord("AMOUNT_DIFF");

        mockMvc.perform(post("/api/admin/reconciliation/records/{reconciliation_id}/check", recordId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-recon-check-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"difference_reason":"商家手续费上浮","checked_flag":true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.difference_reason").value("商家手续费上浮"));

        // Same check is idempotent
        mockMvc.perform(post("/api/admin/reconciliation/records/{reconciliation_id}/check", recordId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-recon-check-1-again")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"difference_reason":"商家手续费上浮","checked_flag":true}
                    """))
            .andExpect(status().isOk());

        Integer flag = jdbcTemplate.queryForObject(
            "select checked_flag from finance_reconciliation_record where id = ?",
            Integer.class, recordId);
        assertThat(flag).isEqualTo(1);

        // Reject missing difference reason on diff record
        long recordId2 = seedReconciliationRecord("FEE_DIFF");
        mockMvc.perform(post("/api/admin/reconciliation/records/{reconciliation_id}/check", recordId2)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-recon-check-no-reason")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"checked_flag":true}
                    """))
            .andExpect(status().isBadRequest());
    }

    // ---- seed helpers (each call inserts a fresh row via @Transactional rollback) ----

    private long seedStudent(String studentNo, String mobile) {
        long studentId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into edu_student (id, student_no, mobile, nickname, status, created_by, updated_by)
            values (?, ?, ?, ?, 'ACTIVE', 0, 0)
            """,
            studentId, studentNo, mobile, "S8 " + studentNo);
        return studentId;
    }

    private long seedPayment(String paymentNo, String merchantOrderNo, long amountCent, String result, LocalDateTime paidAt) {
        long paymentId = SEQUENCE.incrementAndGet();
        long orderId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into pay_payment (id, payment_no, order_id, order_no, merchant_order_no,
                channel, payment_method, payment_result, paid_amount_cent, external_payment_no,
                paid_at, callback_event_no, idempotency_key, created_by, updated_by)
            values (?, ?, ?, ?, ?, 'WECHAT_PAY', 'JSAPI', ?, ?, ?, ?, ?, ?, 0, 0)
            """,
            paymentId, paymentNo, orderId, "S8_ORD_" + orderId, merchantOrderNo, result, amountCent,
            "S8_EXT_" + paymentId, paidAt, "S8_EVT_" + paymentId, "S8_IDEM_" + paymentId);
        return paymentId;
    }

    private long seedEntitlement(long studentId, String status) {
        long entId = SEQUENCE.incrementAndGet();
        long orderId = SEQUENCE.incrementAndGet();
        long userId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into learning_entitlement (id, entitlement_no, student_id, user_id, order_id, order_no,
                order_item_id, course_id, spec_id, status, opened_at, course_snapshot, created_by, updated_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
            """,
            entId, "S8_ENT_" + entId, studentId, userId, orderId, "S8_EORD_" + orderId,
            entId, 100L, 200L, status, LocalDateTime.now(), "{\"name\":\"S8 Course\"}");
        return entId;
    }

    private void seedRefundReview() {
        long refundId = SEQUENCE.incrementAndGet();
        long orderId = SEQUENCE.incrementAndGet();
        long studentId = SEQUENCE.incrementAndGet();
        long paymentId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into pay_refund (id, refund_no, order_id, order_no, payment_id, student_id,
                apply_amount_cent, refund_reason, status, entitlement_action, idempotency_key,
                created_by, updated_by)
            values (?, ?, ?, ?, ?, ?, ?, ?, 'REVIEWING', 'FREEZE', ?, 0, 0)
            """,
            refundId, "S8_RF_" + refundId, orderId, "S8_ORD_" + orderId, paymentId, studentId,
            10000L, "S8 待审核", "S8_REVIEW_" + refundId);
    }

    private void seedPurchaseApproving() {
        long purchaseId = SEQUENCE.incrementAndGet();
        long supplierId = SEQUENCE.incrementAndGet();
        long applicantUserId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into supplier (id, supplier_no, supplier_name, contact_name, contact_mobile,
                status, access_status, created_by, updated_by)
            values (?, ?, ?, 'S8 Contact', '13800009999', 'ACTIVE', 'ACTIVE', 0, 0)
            """,
            supplierId, "S8_SUP_" + supplierId, "S8 Auto Supplier " + supplierId);
        jdbcTemplate.update(
            """
            insert into purchase_order (id, purchase_no, supplier_id, applicant_user_id,
                total_amount_cent, purchase_status, input_invoice_status, created_by, updated_by)
            values (?, ?, ?, ?, ?, 'APPROVING', 'NOT_INVOICED', 0, 0)
            """,
            purchaseId, "S8_PUR_" + purchaseId, supplierId, applicantUserId, 999900L);
    }

    private long seedFailedRefund() {
        long refundId = SEQUENCE.incrementAndGet();
        long orderId = SEQUENCE.incrementAndGet();
        long studentId = SEQUENCE.incrementAndGet();
        long userId = SEQUENCE.incrementAndGet();
        long paymentId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into trade_order (id, order_no, merchant_order_no, student_id, user_id, source_channel,
                total_amount_cent, payable_amount_cent, paid_amount_cent, payment_status,
                fulfillment_status, refund_status, invoice_status, payment_expire_at,
                course_snapshot, price_snapshot, tax_snapshot, created_by, updated_by)
            values (?, ?, ?, ?, ?, 'S8', 10000, 10000, 10000, 'PAID', 'NO_SHIPMENT', 'FAILED',
                    'NOT_APPLIED', ?, '{}', '{}', '{}', 0, 0)
            """,
            orderId, "S8_TO_" + orderId, "S8_MOR_" + orderId, studentId, userId,
            LocalDateTime.now().plusHours(1));
        jdbcTemplate.update(
            """
            insert into pay_refund (id, refund_no, order_id, order_no, payment_id, student_id,
                apply_amount_cent, approved_amount_cent, refund_reason, status, refund_channel,
                entitlement_action, failure_reason, idempotency_key, created_by, updated_by)
            values (?, ?, ?, ?, ?, ?, 10000, 10000, 'S8 failed', 'FAILED', 'ORIGINAL', 'FREEZE',
                    'Mock 失败', ?, 0, 0)
            """,
            refundId, "S8_RFFAIL_" + refundId, orderId, "S8_TO_" + orderId, paymentId, studentId,
            "S8_RFIDEM_" + refundId);
        return refundId;
    }

    private long seedReconciliationRecord(String result) {
        long batchId = SEQUENCE.incrementAndGet();
        long recordId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into finance_reconciliation_batch (id, batch_no, bill_month, bill_source,
                file_name, file_digest, import_status, total_count, matched_count, diff_count,
                created_by, updated_by)
            values (?, ?, '2026-05', 'WECHAT', 's8-recon.csv', ?, 'IMPORTED', 1, 0, 1, 0, 0)
            """,
            batchId, "S8_RB_" + batchId, "S8_DIG_" + batchId);
        jdbcTemplate.update(
            """
            insert into finance_reconciliation_record (id, batch_id, batch_no, bill_month, bill_source,
                record_type, system_amount_cent, bill_amount_cent, fee_amount_cent, result,
                checked_flag, created_by, updated_by)
            values (?, ?, ?, '2026-05', 'WECHAT', 'PAYMENT', 10000, 10100, 0, ?, 0, 0, 0)
            """,
            recordId, batchId, "S8_RB_" + batchId, result);
        return recordId;
    }

    private String adminLogin(String userNo) throws Exception {
        String response = mockMvc.perform(post("/api/admin/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"user_no\":\"%s\"}".formatted(userNo)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "access_token");
    }

    private static long extractLong(String json, String key) {
        String marker = "\"" + key + "\":";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            throw new IllegalStateException("missing " + key + " in " + json);
        }
        int start = idx + marker.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(json.substring(start, end));
    }

    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            throw new IllegalStateException("missing " + key + " in " + json);
        }
        int start = idx + marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
