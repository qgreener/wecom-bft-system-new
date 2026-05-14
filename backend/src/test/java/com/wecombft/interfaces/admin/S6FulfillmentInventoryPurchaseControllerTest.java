package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
class S6FulfillmentInventoryPurchaseControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(6000);
    private static final long TEXTBOOK_SKU_ID = 100000000402L;
    private static final long GIFT_SKU_ID = 100000000403L;
    private static final long SUPPLIER_ID = 100000000301L;
    private static final long STUDENT_ID = 100000000302L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_manage_sku_and_manual_stock_flows_idempotently() throws Exception {
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        long suffix = SEQUENCE.incrementAndGet();

        String skuResponse = mockMvc.perform(post("/api/admin/inventory/skus")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-sku-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sku_name": "S6 手动库存 SKU %d",
                      "category_code": "S6_MANUAL",
                      "sku_type": "MATERIAL",
                      "unit": "件",
                      "spec_attrs": {"batch":"%d"},
                      "default_supplier_id": %d,
                      "cost_price_cent": 1200,
                      "safety_stock": 2,
                      "status": "ACTIVE"
                    }
                    """.formatted(suffix, suffix, SUPPLIER_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.sku_id").exists())
            .andExpect(jsonPath("$.data.available_stock").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
        long skuId = extractLong(skuResponse, "sku_id");

        mockMvc.perform(post("/api/admin/inventory/stock-flows")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-manual-in-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sku_id": %d,
                      "direction": "IN",
                      "quantity": 10,
                      "biz_type": "MANUAL",
                      "biz_no": "S6-MANUAL-IN-%d",
                      "remark": "S6 manual inbound"
                    }
                    """.formatted(skuId, suffix)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.after_stock").value(10));

        mockMvc.perform(post("/api/admin/inventory/stock-flows")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-manual-in-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sku_id": %d,
                      "direction": "IN",
                      "quantity": 10,
                      "biz_type": "MANUAL",
                      "biz_no": "S6-MANUAL-IN-%d",
                      "remark": "S6 duplicate inbound"
                    }
                    """.formatted(skuId, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.after_stock").value(10));

        mockMvc.perform(post("/api/admin/inventory/stock-flows")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-manual-out-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sku_id": %d,
                      "direction": "OUT",
                      "quantity": 3,
                      "biz_type": "MANUAL",
                      "biz_no": "S6-MANUAL-OUT-%d",
                      "remark": "S6 manual outbound"
                    }
                    """.formatted(skuId, suffix)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.after_stock").value(7));

        assertThat(stockOf(skuId)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from inventory_stock_flow where sku_id = ?",
                Integer.class,
                skuId)).isEqualTo(2);
    }

    @Test
    void should_ship_paid_physical_order_deduct_main_and_gift_sku_idempotently() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        long addressId = seedAddress();
        long orderId = createPaidPhysicalOrder(studentToken, addressId, "s6-ship-paid", 188800);
        long shipmentId = findLong("fulfillment_shipment", "id", "order_id", orderId);
        resetStock(TEXTBOOK_SKU_ID, 100);
        resetStock(GIFT_SKU_ID, 100);

        mockMvc.perform(post("/api/admin/shipments/{shipment_id}/ship", shipmentId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-ship-" + shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "logistics_company_code": "mock-express",
                      "logistics_company_name": "Mock Express",
                      "tracking_no": "S6TRACK%s",
                      "waybill_file": "FILE_S6_WAYBILL_%s",
                      "remark": "S6 ship"
                    }
                    """.formatted(shipmentId, shipmentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SHIPPED"))
            .andExpect(jsonPath("$.data.stock_flow_ids", hasSize(2)));

        mockMvc.perform(post("/api/admin/shipments/{shipment_id}/ship", shipmentId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-ship-" + shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "logistics_company_code": "mock-express",
                      "logistics_company_name": "Mock Express",
                      "tracking_no": "S6TRACK%s",
                      "waybill_file": "FILE_S6_WAYBILL_%s",
                      "remark": "S6 duplicate ship"
                    }
                    """.formatted(shipmentId, shipmentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SHIPPED"));

        assertThat(findString("trade_order", "fulfillment_status", orderId)).isEqualTo("SHIPPED");
        assertThat(findString("fulfillment_shipment", "status", shipmentId)).isEqualTo("SHIPPED");
        assertThat(stockOf(TEXTBOOK_SKU_ID)).isEqualTo(99);
        assertThat(stockOf(GIFT_SKU_ID)).isEqualTo(99);
        assertThat(countRows("inventory_stock_flow", "shipment_id", shipmentId)).isEqualTo(2);

        mockMvc.perform(get("/api/admin/orders/{order_id}", orderId)
                .header("Authorization", "Bearer " + warehouseToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.document_links[*].document_type", hasItem("SHIPMENT")))
            .andExpect(jsonPath("$.data.document_links[*].document_type", hasItem("STOCK_FLOW")));
    }

    @Test
    void should_block_stock_shortage_without_partial_deduction() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        long addressId = seedAddress();
        long orderId = createPaidPhysicalOrder(studentToken, addressId, "s6-stock-shortage", 188800);
        long shipmentId = findLong("fulfillment_shipment", "id", "order_id", orderId);
        resetStock(TEXTBOOK_SKU_ID, 0);
        resetStock(GIFT_SKU_ID, 100);

        mockMvc.perform(post("/api/admin/shipments/{shipment_id}/ship", shipmentId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-shortage-" + shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "logistics_company_code": "mock-express",
                      "logistics_company_name": "Mock Express",
                      "tracking_no": "S6SHORT%s",
                      "waybill_file": "FILE_S6_SHORT_%s"
                    }
                    """.formatted(shipmentId, shipmentId)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVENTORY_NOT_ENOUGH"));

        assertThat(findString("fulfillment_shipment", "status", shipmentId)).isEqualTo("PENDING_SHIPMENT");
        assertThat(findString("trade_order", "fulfillment_status", orderId)).isEqualTo("PENDING_SHIPMENT");
        assertThat(stockOf(TEXTBOOK_SKU_ID)).isEqualTo(0);
        assertThat(stockOf(GIFT_SKU_ID)).isEqualTo(100);
        assertThat(countRows("inventory_stock_flow", "shipment_id", shipmentId)).isZero();
    }

    @Test
    void should_record_logistics_callback_idempotently_and_not_regress_signed_status() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        long addressId = seedAddress();
        long orderId = createPaidPhysicalOrder(studentToken, addressId, "s6-logistics", 188800);
        long shipmentId = findLong("fulfillment_shipment", "id", "order_id", orderId);
        String shipmentNo = findString("fulfillment_shipment", "shipment_no", shipmentId);
        resetStock(TEXTBOOK_SKU_ID, 100);
        resetStock(GIFT_SKU_ID, 100);
        String trackingNo = "S6LOG" + shipmentId;
        ship(warehouseToken, shipmentId, trackingNo);

        String tracePayload = """
            {
              "event_no": "S6_TRACE_%s",
              "shipment_no": "%s",
              "tracking_no": "%s",
              "logistics_node_time": "2026-05-14T16:00:00",
              "node_status": "IN_TRANSIT",
              "node_desc": "运输中",
              "signed_flag": false,
              "raw_snapshot": {"scenario":"in_transit"}
            }
            """.formatted(shipmentId, shipmentNo, trackingNo);
        mockMvc.perform(post("/api/callbacks/logistics/traces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tracePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processing_status").value("PROCESSED"))
            .andExpect(jsonPath("$.data.status").value("SHIPPED"));

        mockMvc.perform(post("/api/callbacks/logistics/traces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tracePayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SHIPPED"));
        assertThat(countRows("logistics_trace", "shipment_id", shipmentId)).isEqualTo(1);

        mockMvc.perform(post("/api/callbacks/logistics/traces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S6_SIGN_%s",
                      "shipment_no": "%s",
                      "tracking_no": "%s",
                      "logistics_node_time": "2026-05-14T18:00:00",
                      "node_status": "SIGNED",
                      "node_desc": "已签收",
                      "signed_flag": true,
                      "raw_snapshot": {"scenario":"signed"}
                    }
                    """.formatted(shipmentId, shipmentNo, trackingNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SIGNED"));

        mockMvc.perform(post("/api/callbacks/logistics/traces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S6_OLD_TRACE_%s",
                      "shipment_no": "%s",
                      "tracking_no": "%s",
                      "logistics_node_time": "2026-05-14T15:30:00",
                      "node_status": "PICKED",
                      "node_desc": "已揽收",
                      "signed_flag": false,
                      "raw_snapshot": {"scenario":"late_old_trace"}
                    }
                    """.formatted(shipmentId, shipmentNo, trackingNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("SIGNED"));

        assertThat(findString("fulfillment_shipment", "status", shipmentId)).isEqualTo("SIGNED");
        assertThat(findString("trade_order", "fulfillment_status", orderId)).isEqualTo("SIGNED");
        assertThat(countRows("logistics_trace", "shipment_id", shipmentId)).isEqualTo(3);
        assertThat(documentTypes(orderId)).contains("LOGISTICS_TRACE");
    }

    @Test
    void should_create_large_purchase_approve_supplier_confirm_and_receive_idempotently() throws Exception {
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        String adminToken = adminLogin("DEMO_ADMIN");
        String supplierToken = supplierLogin();
        long suffix = SEQUENCE.incrementAndGet();
        resetStock(TEXTBOOK_SKU_ID, 100);

        String rejectPurchaseResponse = createPurchase(warehouseToken, "s6-purchase-reject-" + suffix, 1, 1000);
        long rejectPurchaseId = extractLong(rejectPurchaseResponse, "purchase_id");
        mockMvc.perform(post("/api/supplier-h5/purchases/{purchase_id}/reject", rejectPurchaseId)
                .header("Authorization", "Bearer " + supplierToken)
                .header("Idempotency-Key", "s6-supplier-reject-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplier_reject_reason\":\"暂时缺货\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.purchase_status").value("REJECTED"));

        String largePurchaseResponse = createPurchase(warehouseToken, "s6-purchase-large-" + suffix, 3, 200000);
        long purchaseId = extractLong(largePurchaseResponse, "purchase_id");
        long approvalId = extractLong(largePurchaseResponse, "approval_id");
        assertThat(findString("purchase_order", "purchase_status", purchaseId)).isEqualTo("APPROVING");

        mockMvc.perform(get("/api/supplier-h5/purchases/{purchase_id}", purchaseId)
                .header("Authorization", "Bearer " + supplierToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/collab/purchase-approvals/{approval_id}/actions", approvalId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\",\"comment\":\"S6 大额采购审批通过\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.purchase_status").value("WAIT_CONFIRM"));

        mockMvc.perform(post("/api/supplier-h5/purchases/{purchase_id}/confirm", purchaseId)
                .header("Authorization", "Bearer " + supplierToken)
                .header("Idempotency-Key", "s6-supplier-confirm-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expected_arrival_date\":\"2026-05-20\",\"remark\":\"可供货\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.purchase_status").value("CONFIRMED"));

        mockMvc.perform(post("/api/supplier-h5/purchases/{purchase_id}/logistics", purchaseId)
                .header("Authorization", "Bearer " + supplierToken)
                .header("Idempotency-Key", "s6-supplier-logistics-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logistics_company_name\":\"Supplier Express\",\"tracking_no\":\"S6PUR%s\"}".formatted(suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.purchase_status").value("SHIPPED"));

        mockMvc.perform(post("/api/admin/purchases/{purchase_id}/receive", purchaseId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-receive-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inbound_batch_no": "S6-IN-%d",
                      "received_items": [
                        {"sku_id": %d, "received_quantity": 3}
                      ],
                      "remark": "S6 receive"
                    }
                    """.formatted(suffix, TEXTBOOK_SKU_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.purchase_status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.stock_flow_ids", hasSize(1)));

        mockMvc.perform(post("/api/admin/purchases/{purchase_id}/receive", purchaseId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-receive-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inbound_batch_no": "S6-IN-%d",
                      "received_items": [
                        {"sku_id": %d, "received_quantity": 3}
                      ],
                      "remark": "S6 duplicate receive"
                    }
                    """.formatted(suffix, TEXTBOOK_SKU_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.purchase_status").value("COMPLETED"));

        assertThat(stockOf(TEXTBOOK_SKU_ID)).isEqualTo(103);
        assertThat(countRows("purchase_receipt", "purchase_id", purchaseId)).isEqualTo(1);
        assertThat(countRows("inventory_stock_flow", "purchase_id", purchaseId)).isEqualTo(1);
        assertThat(findString("purchase_order", "purchase_status", purchaseId)).isEqualTo("COMPLETED");
    }

    @Test
    void should_mark_external_waybill_internal_failure_as_exception_without_stock_deduction() throws Exception {
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        long addressId = seedAddress();
        long orderId = createPaidPhysicalOrder(studentToken, addressId, "s6-external-created-internal-failed", 188800);
        long shipmentId = findLong("fulfillment_shipment", "id", "order_id", orderId);
        resetStock(TEXTBOOK_SKU_ID, 100);
        resetStock(GIFT_SKU_ID, 100);

        mockMvc.perform(post("/api/admin/shipments/{shipment_id}/ship", shipmentId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-external-failed-" + shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "logistics_company_code": "mock-express",
                      "logistics_company_name": "Mock Express",
                      "tracking_no": "S6EXTFAIL%s",
                      "waybill_file": "FILE_S6_EXT_FAIL_%s",
                      "mock_scenario": "EXTERNAL_CREATED_INTERNAL_FAILED"
                    }
                    """.formatted(shipmentId, shipmentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_SHIPMENT"))
            .andExpect(jsonPath("$.data.exception_flag").value(true));

        assertThat(findString("fulfillment_shipment", "status", shipmentId)).isEqualTo("PENDING_SHIPMENT");
        assertThat(findString("trade_order", "fulfillment_status", orderId)).isEqualTo("PENDING_SHIPMENT");
        assertThat(stockOf(TEXTBOOK_SKU_ID)).isEqualTo(100);
        assertThat(stockOf(GIFT_SKU_ID)).isEqualTo(100);
        assertThat(countRows("inventory_stock_flow", "shipment_id", shipmentId)).isZero();
    }

    @Test
    void should_backfill_purchase_input_invoice_after_receipt_idempotently() throws Exception {
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        String supplierToken = supplierLogin();
        long suffix = SEQUENCE.incrementAndGet();
        resetStock(TEXTBOOK_SKU_ID, 100);
        String purchaseResponse = createPurchase(warehouseToken, "s6-purchase-invoice-" + suffix, 2, 1200);
        long purchaseId = extractLong(purchaseResponse, "purchase_id");
        supplierConfirmAndShip(supplierToken, purchaseId, suffix);
        receivePurchase(warehouseToken, purchaseId, suffix, 2);

        mockMvc.perform(post("/api/admin/purchases/{purchase_id}/input-invoice", purchaseId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-input-invoice-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "input_invoice_no": "INVS6%d",
                      "input_invoice_amount_cent": 2400,
                      "input_invoice_file": "FILE_S6_INPUT_INVOICE_%d"
                    }
                    """.formatted(suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.input_invoice_status").value("INVOICED"))
            .andExpect(jsonPath("$.data.input_invoice_no").value("INVS6" + suffix))
            .andExpect(jsonPath("$.data.input_invoice_amount_cent").value(2400))
            .andExpect(jsonPath("$.data.input_invoice_file").value("FILE_S6_INPUT_INVOICE_" + suffix));

        mockMvc.perform(post("/api/admin/purchases/{purchase_id}/input-invoice", purchaseId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-input-invoice-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "input_invoice_no": "INVS6%d",
                      "input_invoice_amount_cent": 2400,
                      "input_invoice_file": "FILE_S6_INPUT_INVOICE_%d"
                    }
                    """.formatted(suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.input_invoice_status").value("INVOICED"));

        assertThat(findString("purchase_order", "input_invoice_status", purchaseId)).isEqualTo("INVOICED");
    }

    private String createPurchase(String warehouseToken, String idempotencyKey, int quantity, long unitPriceCent) throws Exception {
        return mockMvc.perform(post("/api/admin/purchases")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "supplier_id": %d,
                      "submit_reason": "S6 purchase",
                      "purchase_items": [
                        {"sku_id": %d, "quantity": %d, "unit_price_cent": %d}
                      ]
                    }
                    """.formatted(SUPPLIER_ID, TEXTBOOK_SKU_ID, quantity, unitPriceCent)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private void supplierConfirmAndShip(String supplierToken, long purchaseId, long suffix) throws Exception {
        mockMvc.perform(post("/api/supplier-h5/purchases/{purchase_id}/confirm", purchaseId)
                .header("Authorization", "Bearer " + supplierToken)
                .header("Idempotency-Key", "s6-supplier-confirm-invoice-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expected_arrival_date\":\"2026-05-20\",\"remark\":\"可供货\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/supplier-h5/purchases/{purchase_id}/logistics", purchaseId)
                .header("Authorization", "Bearer " + supplierToken)
                .header("Idempotency-Key", "s6-supplier-logistics-invoice-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logistics_company_name\":\"Supplier Express\",\"tracking_no\":\"S6PINV%s\"}".formatted(suffix)))
            .andExpect(status().isOk());
    }

    private void receivePurchase(String warehouseToken, long purchaseId, long suffix, int receivedQuantity) throws Exception {
        mockMvc.perform(post("/api/admin/purchases/{purchase_id}/receive", purchaseId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-receive-invoice-" + suffix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inbound_batch_no": "S6-INVOICE-IN-%d",
                      "received_items": [
                        {"sku_id": %d, "received_quantity": %d}
                      ],
                      "remark": "S6 invoice receive"
                    }
                    """.formatted(suffix, TEXTBOOK_SKU_ID, receivedQuantity)))
            .andExpect(status().isCreated());
    }

    private long createPaidPhysicalOrder(String studentToken, long addressId, String requestNo, long amountCent) throws Exception {
        long courseId = seedCourse("S6 含实物课程 " + requestNo, amountCent);
        long specId = findLong("course_spec", "id", "course_id", courseId);
        long orderId = createOrder(studentToken, courseId, specId, addressId, requestNo, amountCent);
        payOrder(studentToken, orderId, amountCent, "S6_PAY_" + requestNo, "S6PAY-" + requestNo);
        return orderId;
    }

    private long seedCourse(String title, long salePriceCent) {
        long suffix = SEQUENCE.incrementAndGet();
        long courseId = 260000000000L + suffix;
        long specId = 260000100000L + suffix;
        jdbcTemplate.update(
            """
            insert into course (
                id, course_no, course_title, course_type, summary, teacher_user_id,
                category_code, default_tax_rule_id, status, sale_start_at, sale_end_at,
                published_at, created_by, updated_by
            ) values (?, ?, ?, 'LIVE', 'S6 test course', 100000000008, 'S6', 100000000401,
                'ON_SHELF', ?, ?, ?, 0, 0)
            """,
            courseId,
            "S6C" + courseId,
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
            ) values (?, ?, ?, '标准规格', ?, ?, 'LIMITED', 1, ?, ?, 100000000401,
                ?, 'ENABLED', 1, 0, 0)
            """,
            specId,
            "S6S" + specId,
            courseId,
            salePriceCent,
            salePriceCent + 10000,
            TEXTBOOK_SKU_ID,
            GIFT_SKU_ID,
            "{\"training_amount_cent\":" + salePriceCent + "}");
        return courseId;
    }

    private long seedAddress() {
        long addressId = 260000200000L + SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into student_address (
                id, student_id, receiver_name, receiver_mobile, province, city, district,
                detail_address, postal_code, is_default, status, created_by, updated_by
            ) values (?, ?, 'S6 收货人', '13900000009', '浙江省', '杭州市', '西湖区',
                'S6 测试路 1 号', '310000', 1, 'ACTIVE', 0, 0)
            """,
            addressId,
            STUDENT_ID);
        return addressId;
    }

    private long createOrder(String token, long courseId, long specId, long addressId, String requestNo, long amountCent) throws Exception {
        String confirmResponse = mockMvc.perform(post("/api/app/orders/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": 1,
                      "address_id": %d,
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S6_TEST"
                    }
                    """.formatted(courseId, specId, addressId)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String confirmToken = extractString(confirmResponse, "confirm_token");
        String response = mockMvc.perform(post("/api/app/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", requestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": 1,
                      "address_id": %d,
                      "client_request_no": "%s",
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S6_TEST",
                      "confirmed_payable_amount_cent": %d,
                      "confirm_token": "%s"
                    }
                    """.formatted(courseId, specId, addressId, requestNo, amountCent, confirmToken)))
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
                      "paid_at": "2026-05-14T15:20:00",
                      "raw_snapshot": {"scenario":"s6"}
                    }
                    """.formatted(eventNo, externalPaymentNo, amountCent)))
            .andExpect(status().isOk());
    }

    private void ship(String warehouseToken, long shipmentId, String trackingNo) throws Exception {
        mockMvc.perform(post("/api/admin/shipments/{shipment_id}/ship", shipmentId)
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s6-ship-logistics-" + shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "logistics_company_code": "mock-express",
                      "logistics_company_name": "Mock Express",
                      "tracking_no": "%s",
                      "waybill_file": "FILE_S6_LOGISTICS_%s"
                    }
                    """.formatted(trackingNo, shipmentId)))
            .andExpect(status().isOk());
    }

    private String appLogin(String wxCode) throws Exception {
        String response = mockMvc.perform(post("/api/app/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"wx_code\":\"mock:%s\",\"source_channel\":\"S6_TEST\"}".formatted(wxCode)))
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

    private String supplierLogin() throws Exception {
        String response = mockMvc.perform(post("/api/supplier-h5/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"access_token\":\"mock:SUPPLIER_S3\",\"supplier_no\":\"SUP_S3_DEMO\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "session_token");
    }

    private void resetStock(long skuId, int stock) {
        jdbcTemplate.update(
            "update inventory_sku set current_stock = ?, available_stock = ?, locked_stock = 0 where id = ?",
            stock,
            stock,
            skuId);
    }

    private int stockOf(long skuId) {
        return jdbcTemplate.queryForObject("select available_stock from inventory_sku where id = ?", Integer.class, skuId);
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

    private long findLong(String tableName, String columnName, String whereColumnName, long value) {
        return jdbcTemplate.queryForObject(
            "select " + columnName + " from " + tableName + " where " + whereColumnName + " = ?",
            Long.class,
            value);
    }

    private java.util.List<String> documentTypes(long orderId) {
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
