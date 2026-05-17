package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
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
class S7AfterSalesFinanceControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(7000);
    private static final long TEXTBOOK_SKU_ID = 100000000402L;
    private static final long GIFT_SKU_ID = 100000000403L;
    private static final long STUDENT_ID = 100000000302L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_apply_review_reject_original_and_manual_refunds_without_changing_payment_or_entitlement_during_review() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String serviceToken = adminLogin("DEMO_SERVICE");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long rejectedOrderId = createPaidOrder(studentToken, false, null, "s7-refund-rejected", 58800);
        long originalOrderId = createPaidOrder(studentToken, false, null, "s7-refund-original", 68800);
        long manualOrderId = createPaidOrder(studentToken, false, null, "s7-refund-manual", 78800);

        long rejectedRefundId = applyRefund(studentToken, rejectedOrderId, 58800, "s7-refund-rejected-key", "FREEZE");
        assertThat(findString("trade_order", "payment_status", rejectedOrderId)).isEqualTo("PAID");
        assertThat(findString("trade_order", "refund_status", rejectedOrderId)).isEqualTo("REVIEWING");
        assertThat(findStringBy("learning_entitlement", "status", "order_id", rejectedOrderId)).isEqualTo("ACTIVE");

        mockMvc.perform(post("/api/app/orders/{order_id}/refunds", rejectedOrderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s7-refund-duplicate-running")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"apply_amount_cent":58800,"refund_reason":"重复申请"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        mockMvc.perform(post("/api/admin/refunds/{refund_id}/review", rejectedRefundId)
                .header("Authorization", "Bearer " + serviceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"REJECT\",\"reject_reason\":\"不符合退款规则\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));
        assertThat(findString("trade_order", "refund_status", rejectedOrderId)).isEqualTo("REJECTED");
        assertThat(findStringBy("learning_entitlement", "status", "order_id", rejectedOrderId)).isEqualTo("ACTIVE");

        long originalRefundId = applyRefund(studentToken, originalOrderId, 68800, "s7-refund-original-key", "REVOKE");
        mockMvc.perform(post("/api/admin/refunds/{refund_id}/review", originalRefundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s7-approve-original")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approved_amount_cent":68800,"refund_channel":"ORIGINAL","review_comment":"同意原路退","entitlement_action":"REVOKE"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"))
            .andExpect(jsonPath("$.data.refund_channel").value("ORIGINAL"));

        long manualRefundId = applyRefund(studentToken, manualOrderId, 78800, "s7-refund-manual-key", "FREEZE");
        mockMvc.perform(post("/api/admin/refunds/{refund_id}/review", manualRefundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s7-approve-manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approved_amount_cent":78800,"refund_channel":"MANUAL","review_comment":"转线下处理","entitlement_action":"FREEZE"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MANUAL_REQUIRED"));

        mockMvc.perform(post("/api/admin/refunds/{refund_id}/manual-complete", manualRefundId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-manual-complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "refund_channel": "MANUAL",
                      "manual_voucher_no": "S7MANUAL001",
                      "manual_voucher_file": "FILE_S7_MANUAL_001",
                      "refunded_at": "2026-05-15T10:10:00",
                      "entitlement_action": "FREEZE",
                      "remark": "线下已退"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFUNDED"))
            .andExpect(jsonPath("$.data.manual_voucher_no").value("S7MANUAL001"));
    }

    @Test
    void should_handle_refund_callback_idempotently_and_apply_success_side_effects_once() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String serviceToken = adminLogin("DEMO_SERVICE");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long addressId = seedAddress();
        resetStock(TEXTBOOK_SKU_ID, 20);
        resetStock(GIFT_SKU_ID, 20);
        long orderId = createPaidOrder(studentToken, true, addressId, "s7-refund-side-effects", 99800);
        long titleId = saveInvoiceTitle(studentToken, "s7-title-side-effects", "COMPANY", "S7 副作用公司", "91330100S7SIDE", "side@example.test");
        long invoiceId = applyInvoice(studentToken, orderId, titleId, "s7-invoice-side-effects");
        issueInvoice(accountingToken, invoiceId, "S7INV-SIDE", "FILE_S7_INVOICE_SIDE");
        long refundId = applyRefund(studentToken, orderId, 99800, "s7-refund-side-effects-key", "REVOKE");
        String refundNo = findString("pay_refund", "refund_no", refundId);
        approveOriginal(serviceToken, refundId, 99800, "REVOKE", "s7-approve-side-effects");

        String successCallback = """
            {
              "event_no": "S7_REFUND_SUCCESS_%d",
              "refund_no": "%s",
              "external_refund_no": "S7EXTREFUND%d",
              "refund_status": "SUCCESS",
              "refunded_amount_cent": 99800,
              "refunded_at": "2026-05-15T11:00:00",
              "raw_snapshot": {"scenario":"success"}
            }
            """.formatted(refundId, refundNo, refundId);
        mockMvc.perform(post("/api/callbacks/refunds/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(successCallback))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processing_status").value("PROCESSED"))
            .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        mockMvc.perform(post("/api/callbacks/refunds/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(successCallback))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        mockMvc.perform(post("/api/callbacks/refunds/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S7_REFUND_LATE_FAIL_%d",
                      "refund_no": "%s",
                      "external_refund_no": "S7EXTREFUND-LATE-%d",
                      "refund_status": "FAILED",
                      "refunded_amount_cent": 99800,
                      "failure_reason": "乱序失败回调",
                      "raw_snapshot": {"scenario":"late_failed"}
                    }
                    """.formatted(refundId, refundNo, refundId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        assertThat(findString("pay_refund", "status", refundId)).isEqualTo("REFUNDED");
        assertThat(findString("trade_order", "refund_status", orderId)).isEqualTo("REFUNDED");
        assertThat(findStringBy("learning_entitlement", "status", "order_id", orderId)).isEqualTo("REVOKED");
        assertThat(findIntBy("learning_entitlement", "remind_stopped", "order_id", orderId)).isEqualTo(1);
        assertThat(findIntBy("fulfillment_shipment", "exception_flag", "order_id", orderId)).isEqualTo(1);
        assertThat(findLongNullable("tax_invoice", "source_refund_id", invoiceId)).isEqualTo(refundId);
        assertThat(countRows("acct_material", "related_object_id", refundId)).isEqualTo(1);
        assertThat(documentTypes(orderId)).contains("REFUND", "INVOICE", "ACCOUNTING_MATERIAL");
    }

    @Test
    void should_save_invoice_title_apply_invoice_manual_issue_and_red_reverse_with_blocks() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long unpaidOrderId = createOrder(studentToken, false, null, "s7-unpaid-invoice", 31800);
        long titleId = saveInvoiceTitle(studentToken, "s7-title-main", "COMPANY", "S7 开票公司", "91330100S7INV", "invoice@example.test");

        mockMvc.perform(post("/api/app/orders/{order_id}/invoices", unpaidOrderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s7-invoice-unpaid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title_id":%d,"email":"invoice@example.test"}
                    """.formatted(titleId)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));

        long orderId = createPaidOrder(studentToken, false, null, "s7-invoice-paid", 31800);
        long invoiceId = applyInvoice(studentToken, orderId, titleId, "s7-invoice-apply");
        assertThat(findLong("tax_invoice", "invoice_amount_cent", invoiceId)).isEqualTo(31800L);

        mockMvc.perform(post("/api/callbacks/invoices/issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S7_INVOICE_FAIL_%d",
                      "invoice_apply_no": "%s",
                      "status": "FAILED",
                      "failure_reason": "Mock 自动开票失败",
                      "raw_snapshot": {"scenario":"issue_failed"}
                    }
                    """.formatted(invoiceId, findString("tax_invoice", "invoice_apply_no", invoiceId))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("TO_BE_ISSUED"));

        issueInvoice(accountingToken, invoiceId, "S7INV-MAIN", "FILE_S7_INVOICE_MAIN");
        assertThat(findString("trade_order", "invoice_status", orderId)).isEqualTo("ISSUED");

        mockMvc.perform(post("/api/callbacks/invoices/red-reverse")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S7_RED_FAIL_%d",
                      "invoice_apply_no": "%s",
                      "status": "FAILED",
                      "failure_reason": "Mock 红冲失败",
                      "raw_snapshot": {"scenario":"red_failed"}
                    }
                    """.formatted(invoiceId, findString("tax_invoice", "invoice_apply_no", invoiceId))))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("ISSUED"));
        assertThat(findString("tax_invoice", "failure_reason", invoiceId)).isEqualTo("Mock 红冲失败");

        mockMvc.perform(post("/api/admin/invoices/{invoice_id}/red-reverse", invoiceId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-red-manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "red_invoice_no": "S7RED-MAIN",
                      "red_invoice_file": "FILE_S7_RED_MAIN",
                      "red_reversed_at": "2026-05-15T12:00:00",
                      "remark": "人工红冲"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RED_REVERSED"));
        assertThat(findString("trade_order", "invoice_status", orderId)).isEqualTo("RED_REVERSED");

        long refundingOrderId = createPaidOrder(studentToken, false, null, "s7-invoice-refunding", 41800);
        applyRefund(studentToken, refundingOrderId, 41800, "s7-refunding-before-invoice", "FREEZE");
        mockMvc.perform(post("/api/app/orders/{order_id}/invoices", refundingOrderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s7-invoice-refunding-block")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title_id":%d,"email":"invoice@example.test"}
                    """.formatted(titleId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void should_accept_documented_s7_api_paths_end_to_end() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String serviceToken = adminLogin("DEMO_SERVICE");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long refundOrderId = createPaidOrder(studentToken, false, null, "s7-api-contract-refund", 33800);

        String refundResponse = mockMvc.perform(post("/api/app/orders/{order_id}/refunds", refundOrderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s7-api-contract-refund")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "apply_amount_cent": 33800,
                      "refund_reason": "契约路径退款",
                      "apply_description": "contract alias",
                      "entitlement_action": "FREEZE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.order_id").value(refundOrderId))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long refundId = extractLong(refundResponse, "refund_id");

        mockMvc.perform(post("/api/admin/refunds/{refund_id}/review", refundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s7-api-contract-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"REJECT","reject_reason":"契约路径拒绝退款"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        long invoiceOrderId = createPaidOrder(studentToken, false, null, "s7-api-contract-invoice", 34800);
        long titleId = saveInvoiceTitle(studentToken, "s7-api-contract-title", "COMPANY", "S7 契约公司", "91330100S7API", "api@example.test");
        String invoiceResponse = mockMvc.perform(post("/api/app/orders/{order_id}/invoices", invoiceOrderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s7-api-contract-invoice")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title_id":%d,"email":"api@example.test"}
                    """.formatted(titleId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.order_id").value(invoiceOrderId))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long invoiceId = extractLong(invoiceResponse, "invoice_id");

        mockMvc.perform(post("/api/admin/invoices/{invoice_id}/issue-manual", invoiceId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-api-contract-issue")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "invoice_no": "S7API-ISSUE",
                      "invoice_file": "FILE_S7_API_ISSUE",
                      "issued_at": "2026-05-15T10:30:00",
                      "remark": "契约路径人工开票"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"));

        String batchResponse = mockMvc.perform(post("/api/admin/reconciliation/batches")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-api-contract-recon")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bill_month": "2026-05",
                      "bill_source": "WECHAT",
                      "file_name": "s7-api-contract.csv",
                      "file_digest": "S7_API_CONTRACT_RECON",
                      "records": []
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long batchId = extractLong(batchResponse, "batch_id");

        mockMvc.perform(get("/api/admin/reconciliation/batches/{batch_id}/records", batchId)
                .header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records", hasSize(0)));

        String materialResponse = mockMvc.perform(post("/api/admin/accounting/materials")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-api-contract-material")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "material_type": "MONTHLY_BOOKKEEPING",
                      "related_month": "2026-05",
                      "order_id": %d,
                      "related_object_type": "ORDER",
                      "related_object_id": %d,
                      "purpose": "契约路径材料",
                      "due_at": "2026-05-31T23:59:59"
                    }
                    """.formatted(invoiceOrderId, invoiceOrderId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long materialId = extractLong(materialResponse, "material_id");

        mockMvc.perform(post("/api/admin/accounting/materials/{material_id}/actions", materialId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-api-contract-material-upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"UPLOAD","file_refs":["FILE_S7_API_MATERIAL"],"remark":"契约路径上传"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UPLOADED"));

        mockMvc.perform(post("/api/admin/accounting/materials/{material_id}/actions", materialId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-api-contract-material-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"CONFIRM","remark":"契约路径确认"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void should_expose_admin_finance_lists_for_s8_pc_pages() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String serviceToken = adminLogin("DEMO_SERVICE");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long refundOrderId = createPaidOrder(studentToken, false, null, "s8-admin-refund-list", 35800);
        long refundId = applyRefund(studentToken, refundOrderId, 35800, "s8-admin-refund-list", "FREEZE");
        String refundNo = findString("pay_refund", "refund_no", refundId);

        mockMvc.perform(get("/api/admin/refunds")
                .header("Authorization", "Bearer " + serviceToken)
                .param("status", "REVIEWING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].refund_no", hasItem(refundNo)));
        mockMvc.perform(get("/api/admin/refunds/{refund_id}", refundId)
                .header("Authorization", "Bearer " + serviceToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.refund_id").value(refundId));

        long invoiceOrderId = createPaidOrder(studentToken, false, null, "s8-admin-invoice-list", 36800);
        long titleId = saveInvoiceTitle(studentToken, "s8-admin-invoice-title", "COMPANY", "S8 开票公司", "91330100S8INV", "s8@example.test");
        long invoiceId = applyInvoice(studentToken, invoiceOrderId, titleId, "s8-admin-invoice-list");
        String invoiceApplyNo = findString("tax_invoice", "invoice_apply_no", invoiceId);

        mockMvc.perform(get("/api/admin/invoices")
                .header("Authorization", "Bearer " + accountingToken)
                .param("status", "APPLIED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].invoice_apply_no", hasItem(invoiceApplyNo)));
        mockMvc.perform(get("/api/admin/invoices/{invoice_id}", invoiceId)
                .header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.invoice_id").value(invoiceId));

        String materialResponse = mockMvc.perform(post("/api/admin/accounting/materials")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s8-admin-material-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "material_type": "MONTHLY_BOOKKEEPING",
                      "related_month": "2026-05",
                      "order_id": %d,
                      "related_object_type": "ORDER",
                      "related_object_id": %d,
                      "purpose": "S8 管理端材料列表",
                      "due_at": "2026-05-31T23:59:59"
                    }
                    """.formatted(invoiceOrderId, invoiceOrderId)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long materialId = extractLong(materialResponse, "material_id");
        String materialNo = findString("acct_material", "material_no", materialId);

        mockMvc.perform(get("/api/admin/accounting/materials")
                .header("Authorization", "Bearer " + accountingToken)
                .param("status", "PENDING_SUPPLEMENT")
                .param("related_month", "2026-05"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].material_no", hasItem(materialNo)));
        mockMvc.perform(get("/api/admin/accounting/materials/{material_id}", materialId)
                .header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.material_id").value(materialId));
    }

    @Test
    void should_import_reconciliation_records_without_mutating_payment_or_refund_status() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String serviceToken = adminLogin("DEMO_SERVICE");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        long orderId = createPaidOrder(studentToken, false, null, "s7-recon-paid", 56800);
        String merchantOrderNo = findString("trade_order", "merchant_order_no", orderId);
        String paymentStatusBefore = findString("trade_order", "payment_status", orderId);
        long refundOrderId = createPaidOrder(studentToken, false, null, "s7-recon-refund", 46800);
        String refundMerchantOrderNo = findString("trade_order", "merchant_order_no", refundOrderId);
        long refundId = applyRefund(studentToken, refundOrderId, 46800, "s7-recon-refund-key", "FREEZE");
        String refundNo = findString("pay_refund", "refund_no", refundId);
        String externalRefundNo = "S7EXTRECON" + refundId;
        approveOriginal(serviceToken, refundId, 46800, "FREEZE", "s7-approve-recon-refund");
        mockMvc.perform(post("/api/callbacks/refunds/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S7_REFUND_RECON_%d",
                      "refund_no": "%s",
                      "external_refund_no": "%s",
                      "refund_status": "SUCCESS",
                      "refunded_amount_cent": 46800,
                      "refunded_at": "2026-05-15T11:30:00",
                      "raw_snapshot": {"scenario":"recon_refund"}
                    }
                    """.formatted(refundId, refundNo, externalRefundNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFUNDED"));

        String response = mockMvc.perform(post("/api/admin/reconciliation/batches")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-recon-import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bill_month": "2026-05",
                      "bill_source": "WECHAT",
                      "file_name": "s7-recon.csv",
                      "file_digest": "S7_RECON_DIGEST_%d",
                      "records": [
                        {"record_type":"PAYMENT","merchant_order_no":"%s","external_transaction_no":"S7PAY-s7-recon-paid","bill_amount_cent":56800,"fee_amount_cent":0},
                        {"record_type":"PAYMENT","merchant_order_no":"%s","external_transaction_no":"S7PAY-DIFF-%d","bill_amount_cent":56801,"fee_amount_cent":0},
                        {"record_type":"PAYMENT","merchant_order_no":"%s","external_transaction_no":"S7PAY-FEE-%d","bill_amount_cent":56800,"fee_amount_cent":10},
                        {"record_type":"PAYMENT","merchant_order_no":"S7-UNKNOWN-%d","external_transaction_no":"S7PAY-UNKNOWN-%d","bill_amount_cent":100,"fee_amount_cent":0},
                        {"record_type":"PAYMENT","merchant_order_no":"S7-DUP-%d","external_transaction_no":"S7PAY-DUP","bill_amount_cent":1,"fee_amount_cent":0},
                        {"record_type":"PAYMENT","merchant_order_no":"S7-DUP-%d","external_transaction_no":"S7PAY-DUP","bill_amount_cent":1,"fee_amount_cent":0},
                        {"record_type":"REFUND","merchant_order_no":"%s","external_transaction_no":"%s","bill_amount_cent":46800,"fee_amount_cent":0}
                      ]
                    }
                    """.formatted(
                        SEQUENCE.incrementAndGet(),
                        merchantOrderNo,
                        merchantOrderNo,
                        orderId,
                        merchantOrderNo,
                        orderId,
                        orderId,
                        orderId,
                        orderId,
                        orderId,
                        refundMerchantOrderNo,
                        externalRefundNo)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.total_count").value(7))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long batchId = extractLong(response, "batch_id");

        mockMvc.perform(get("/api/admin/reconciliation/batches/{batch_id}", batchId)
                .header("Authorization", "Bearer " + accountingToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].result", hasItem("MATCHED")))
            .andExpect(jsonPath("$.data.records[*].result", hasItem("AMOUNT_DIFF")))
            .andExpect(jsonPath("$.data.records[*].result", hasItem("FEE_DIFF")))
            .andExpect(jsonPath("$.data.records[*].result", hasItem("UNMATCHED")))
            .andExpect(jsonPath("$.data.records[*].result", hasItem("DUPLICATE")));

        assertThat(findString("trade_order", "payment_status", orderId)).isEqualTo(paymentStatusBefore);
        assertThat(findString("pay_refund", "status", refundId)).isEqualTo("REFUNDED");
        assertThat(countRows("finance_reconciliation_record", "batch_id", batchId)).isEqualTo(7);
    }

    @Test
    void should_flow_accounting_materials_and_protect_download_with_audit() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        String serviceToken = adminLogin("DEMO_SERVICE");
        long orderId = createPaidOrder(studentToken, false, null, "s7-material-order", 43800);

        String response = mockMvc.perform(post("/api/admin/accounting/materials")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-material-create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "material_type": "SUPPLEMENT",
                      "related_month": "2026-05",
                      "order_id": %d,
                      "related_object_type": "ORDER",
                      "related_object_id": %d,
                      "purpose": "S7 月度做账补充材料",
                      "due_at": "2026-05-30T18:00:00"
                    }
                    """.formatted(orderId, orderId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("PENDING_SUPPLEMENT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long materialId = extractLong(response, "material_id");

        mockMvc.perform(post("/api/admin/accounting/materials/{material_id}/actions", materialId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-material-upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"UPLOAD","file_refs":["FILE_S7_MATERIAL_001"],"remark":"已上传材料"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UPLOADED"));

        mockMvc.perform(post("/api/admin/accounting/materials/{material_id}/actions", materialId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-material-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CONFIRM\",\"remark\":\"材料可用\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/admin/accounting/materials/{material_id}/download", materialId)
                .header("Authorization", "Bearer " + serviceToken)
                .param("file_no", "FILE_S7_MATERIAL_001")
                .param("download_reason", "越权下载"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/accounting/materials/{material_id}/download", materialId)
                .header("Authorization", "Bearer " + accountingToken)
                .param("file_no", "FILE_S7_MATERIAL_001")
                .param("download_reason", "月度做账"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.file_no").value("FILE_S7_MATERIAL_001"))
            .andExpect(jsonPath("$.data.download_allowed").value(true));

        mockMvc.perform(post("/api/admin/accounting/materials/{material_id}/actions", materialId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s7-material-close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"CLOSE\",\"closed_reason\":\"S7 材料归档关闭\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(get("/api/admin/accounting-workbench/summary")
                .header("Authorization", "Bearer " + accountingToken)
                .param("related_month", "2026-05"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.income_amount_cent").value(43800))
            .andExpect(jsonPath("$.data.material_status_counts.CLOSED").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_operation_log where operation_module = 'ACCOUNTING' and target_id = ?",
                Integer.class,
                materialId)).isGreaterThanOrEqualTo(5);
    }

    @Test
    void should_retry_after_sales_finance_compensation_for_failed_refund() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String serviceToken = adminLogin("DEMO_SERVICE");
        long orderId = createPaidOrder(studentToken, false, null, "s7-compensation-refund", 62800);
        long refundId = applyRefund(studentToken, orderId, 62800, "s7-compensation-refund-key", "FREEZE");
        String refundNo = findString("pay_refund", "refund_no", refundId);
        approveOriginal(serviceToken, refundId, 62800, "FREEZE", "s7-approve-compensation");

        mockMvc.perform(post("/api/callbacks/refunds/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S7_REFUND_FAILED_%d",
                      "refund_no": "%s",
                      "external_refund_no": "S7EXTFAIL%d",
                      "refund_status": "FAILED",
                      "refunded_amount_cent": 62800,
                      "failure_reason": "余额不足",
                      "raw_snapshot": {"scenario":"failed"}
                    }
                    """.formatted(refundId, refundNo, refundId)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.status").value("FAILED"));

        mockMvc.perform(post("/api/collab/compensations/{compensation_id}/retry", refundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s7-compensation-retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"compensation_type":"REFUND","action":"MANUAL_REQUIRED","remark":"转人工退款"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.related_object_type").value("REFUND"))
            .andExpect(jsonPath("$.data.processing_status").value("MANUAL_REQUIRED"));
        assertThat(findString("pay_refund", "status", refundId)).isEqualTo("MANUAL_REQUIRED");
    }

    private long applyRefund(String studentToken, long orderId, long amountCent, String idempotencyKey, String entitlementAction) throws Exception {
        String response = mockMvc.perform(post("/api/app/orders/{order_id}/refunds", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "apply_amount_cent": %d,
                      "refund_reason": "S7 退款",
                      "apply_description": "S7 refund test",
                      "entitlement_action": "%s"
                    }
                    """.formatted(amountCent, entitlementAction)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("REVIEWING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "refund_id");
    }

    private void approveOriginal(String serviceToken, long refundId, long amountCent, String entitlementAction, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/admin/refunds/{refund_id}/review", refundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"APPROVE","approved_amount_cent":%d,"refund_channel":"ORIGINAL","review_comment":"同意","entitlement_action":"%s"}
                    """.formatted(amountCent, entitlementAction)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    private long saveInvoiceTitle(String studentToken, String idempotencyKey, String type, String titleName, String taxNo, String email) throws Exception {
        String response = mockMvc.perform(post("/api/app/invoice-titles")
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title_type": "%s",
                      "title_name": "%s",
                      "tax_no": "%s",
                      "email": "%s",
                      "is_default": true
                    }
                    """.formatted(type, titleName, taxNo, email)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title_id").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "title_id");
    }

    private long applyInvoice(String studentToken, long orderId, long titleId, String idempotencyKey) throws Exception {
        String response = mockMvc.perform(post("/api/app/orders/{order_id}/invoices", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title_id":%d,"email":"invoice@example.test"}
                    """.formatted(titleId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.status").value("APPLIED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "invoice_id");
    }

    private void issueInvoice(String accountingToken, long invoiceId, String invoiceNo, String fileNo) throws Exception {
        mockMvc.perform(post("/api/admin/invoices/{invoice_id}/issue-manual", invoiceId)
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "issue-" + invoiceNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "invoice_no": "%s",
                      "invoice_file": "%s",
                      "issued_at": "2026-05-15T10:30:00",
                      "remark": "人工开票"
                    }
                    """.formatted(invoiceNo, fileNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    private long createPaidOrder(String studentToken, boolean containsPhysical, Long addressId, String requestNo, long amountCent) throws Exception {
        long orderId = createOrder(studentToken, containsPhysical, addressId, requestNo, amountCent);
        payOrder(studentToken, orderId, amountCent, "S7_PAY_" + requestNo, "S7PAY-" + requestNo);
        return orderId;
    }

    private long createOrder(String studentToken, boolean containsPhysical, Long addressId, String requestNo, long amountCent) throws Exception {
        long courseId = seedCourse("S7 课程 " + requestNo, containsPhysical, amountCent);
        long specId = findLongBy("course_spec", "id", "course_id", courseId);
        String confirmResponse = mockMvc.perform(post("/api/app/orders/confirm")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": 1,
                      "address_id": %s,
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S7_TEST"
                    }
                    """.formatted(courseId, specId, addressId == null ? "null" : addressId.toString())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String confirmToken = extractString(confirmResponse, "confirm_token");
        String response = mockMvc.perform(post("/api/app/orders")
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", requestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": 1,
                      "address_id": %s,
                      "client_request_no": "%s",
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S7_TEST",
                      "confirmed_payable_amount_cent": %d,
                      "confirm_token": "%s"
                    }
                    """.formatted(courseId, specId, addressId == null ? "null" : addressId.toString(), requestNo, amountCent, confirmToken)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractLong(response, "order_id");
    }

    private void payOrder(String token, long orderId, long amountCent, String eventNo, String externalPaymentNo) throws Exception {
        mockMvc.perform(post("/api/app/orders/{order_id}/mock-pay", orderId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "%s",
                      "external_payment_no": "%s",
                      "paid_amount_cent": %d,
                      "payment_result": "SUCCESS",
                      "paid_at": "2026-05-15T09:00:00",
                      "raw_snapshot": {"scenario":"s7"}
                    }
                    """.formatted(eventNo, externalPaymentNo, amountCent)))
            .andExpect(status().isOk());
    }

    private long seedCourse(String title, boolean containsPhysical, long salePriceCent) {
        long suffix = SEQUENCE.incrementAndGet();
        long courseId = 270000000000L + suffix;
        long specId = 270000100000L + suffix;
        jdbcTemplate.update(
            """
            insert into course (
                id, course_no, course_title, course_type, summary, teacher_user_id,
                category_code, default_tax_rule_id, status, sale_start_at, sale_end_at,
                published_at, created_by, updated_by
            ) values (?, ?, ?, 'LIVE', 'S7 test course', 100000000008, 'S7', 100000000401,
                'ON_SHELF', ?, ?, ?, 0, 0)
            """,
            courseId,
            "S7C" + courseId,
            title,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now());
        jdbcTemplate.update(
            """
            insert into course_spec (
                id, spec_no, course_id, spec_name, sale_price_cent, origin_price_cent,
                stock_mode, contains_physical, sku_id, gift_sku_id, tax_rule_id,
                amount_split_snapshot, status, sort_no, created_by, updated_by
            ) values (?, ?, ?, '标准规格', ?, ?, 'LIMITED', ?, ?, ?, 100000000401,
                ?, 'ENABLED', 1, 0, 0)
            """,
            specId,
            "S7S" + specId,
            courseId,
            salePriceCent,
            salePriceCent + 10000,
            containsPhysical ? 1 : 0,
            containsPhysical ? TEXTBOOK_SKU_ID : null,
            containsPhysical ? GIFT_SKU_ID : null,
            "{\"training_amount_cent\":" + salePriceCent + "}");
        return courseId;
    }

    private long seedAddress() {
        long addressId = 270000200000L + SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into student_address (
                id, student_id, receiver_name, receiver_mobile, province, city, district,
                detail_address, postal_code, is_default, status, created_by, updated_by
            ) values (?, ?, 'S7 收货人', '13900000009', '浙江省', '杭州市', '西湖区',
                'S7 测试路 1 号', '310000', 1, 'ACTIVE', 0, 0)
            """,
            addressId,
            STUDENT_ID);
        return addressId;
    }

    private String appLogin(String wxCode) throws Exception {
        String response = mockMvc.perform(post("/api/app/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wx_code\":\"mock:%s\",\"source_channel\":\"S7_TEST\"}".formatted(wxCode)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "access_token");
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

    private void resetStock(long skuId, int stock) {
        jdbcTemplate.update(
            "update inventory_sku set current_stock = ?, available_stock = ?, locked_stock = 0 where id = ?",
            stock,
            stock,
            skuId);
    }

    private int countRows(String tableName, String columnName, long value) {
        return jdbcTemplate.queryForObject(
            "select count(*) from " + tableName + " where " + columnName + " = ?",
            Integer.class,
            value);
    }

    private String findString(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where id = ?",
            String.class,
            id);
    }

    private String findStringBy(String tableName, String columnName, String whereColumnName, long value) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where " + whereColumnName + " = ?",
            String.class,
            value);
    }

    private long findLong(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where id = ?",
            Long.class,
            id);
    }

    private long findLongBy(String tableName, String columnName, String whereColumnName, long value) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where " + whereColumnName + " = ?",
            Long.class,
            value);
    }

    private Long findLongNullable(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where id = ?",
            Long.class,
            id);
    }

    private int findIntBy(String tableName, String columnName, String whereColumnName, long value) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where " + whereColumnName + " = ?",
            Integer.class,
            value);
    }

    private List<String> documentTypes(long orderId) {
        return jdbcTemplate.queryForList(
            "select document_type from order_document_link where order_id = ? order by document_type",
            String.class,
            orderId);
    }

    private long extractLong(String response, String fieldName) {
        String marker = "\"" + fieldName + "\":";
        int start = response.indexOf(marker) + marker.length();
        int end = start;
        while (end < response.length() && Character.isDigit(response.charAt(end))) {
            end++;
        }
        return Long.parseLong(response.substring(start, end));
    }

    private String extractString(String response, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int start = response.indexOf(marker) + marker.length();
        int end = response.indexOf('"', start);
        return response.substring(start, end);
    }
}
