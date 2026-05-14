package com.wecombft.interfaces.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
class S5OrderPaymentControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(5000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_confirm_order_snapshot_and_block_unbound_student() throws Exception {
        long courseId = seedCourse("S5 确认快照课程", true, 129900);
        long specId = findSpecId(courseId);
        long addressId = seedAddress(100000000302L);
        String studentToken = appLogin("DEMO_APP_STUDENT");

        mockMvc.perform(post("/api/app/orders/confirm")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": 2,
                      "address_id": %d,
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S5_CONFIRM"
                    }
                    """.formatted(courseId, specId, addressId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.course_snapshot.course_id").value(courseId))
            .andExpect(jsonPath("$.data.course_snapshot.course_title").value("S5 确认快照课程"))
            .andExpect(jsonPath("$.data.price_snapshot.quantity").value(2))
            .andExpect(jsonPath("$.data.total_amount_cent").value(259800))
            .andExpect(jsonPath("$.data.payable_amount_cent").value(259800))
            .andExpect(jsonPath("$.data.tax_snapshot.tax_rule_no").value("TAX_S4_TRAINING"))
            .andExpect(jsonPath("$.data.receiver_snapshot.receiver_mobile").value("13900000009"))
            .andExpect(jsonPath("$.data.contains_physical").value(true))
            .andExpect(jsonPath("$.data.stock_warning").value(false))
            .andExpect(jsonPath("$.data.confirm_token").exists());

        String unboundToken = appLogin("s5_unbound_" + SEQUENCE.incrementAndGet());
        mockMvc.perform(post("/api/app/orders/confirm")
                .header("Authorization", "Bearer " + unboundToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"course_id":%d,"spec_id":%d,"quantity":1,"source_channel":"MINIPROGRAM"}
                    """.formatted(courseId, specId)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));
    }

    @Test
    void should_create_order_idempotently_and_reject_price_change_after_confirm() throws Exception {
        long courseId = seedCourse("S5 幂等创建课程", false, 129900);
        long specId = findSpecId(courseId);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        String confirmResponse = confirm(studentToken, courseId, specId, null, 1);
        String confirmToken = extractString(confirmResponse, "confirm_token");

        String createPayload = """
            {
              "course_id": %d,
              "spec_id": %d,
              "quantity": 1,
              "client_request_no": "s5-create-001",
              "source_channel": "MINIPROGRAM",
              "source_code": "S5_CREATE",
              "confirmed_payable_amount_cent": 129900,
              "confirm_token": "%s"
            }
            """.formatted(courseId, specId, confirmToken);
        String response = mockMvc.perform(post("/api/app/orders")
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s5-create-idempotent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.payment_status").value("PENDING"))
            .andExpect(jsonPath("$.data.fulfillment_status").value("NO_SHIPMENT"))
            .andExpect(jsonPath("$.data.refund_status").value("NONE"))
            .andExpect(jsonPath("$.data.invoice_status").value("NOT_APPLIED"))
            .andExpect(jsonPath("$.data.merchant_order_no").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();
        long orderId = extractLong(response, "order_id");

        mockMvc.perform(post("/api/app/orders")
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s5-create-idempotent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.order_id").value(orderId));
        assertThat(countRows("trade_order_item", "order_id", orderId)).isEqualTo(1);

        String staleConfirm = confirm(studentToken, courseId, specId, null, 1);
        String staleToken = extractString(staleConfirm, "confirm_token");
        jdbcTemplate.update("update course_spec set sale_price_cent = 139900, version = version + 1 where id = ?", specId);

        mockMvc.perform(post("/api/app/orders")
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "s5-create-price-changed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": 1,
                      "client_request_no": "s5-create-002",
                      "source_channel": "MINIPROGRAM",
                      "confirmed_payable_amount_cent": 129900,
                      "confirm_token": "%s"
                    }
                    """.formatted(courseId, specId, staleToken)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));
    }

    @Test
    void should_continue_pay_cancel_and_close_timeout_order() throws Exception {
        long courseId = seedCourse("S5 待支付处理课程", false, 89900);
        long specId = findSpecId(courseId);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long orderId = createOrder(studentToken, courseId, specId, null, 1, "s5-pending-1", 89900);

        mockMvc.perform(post("/api/app/orders/{order_id}/pay", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payment_channel\":\"MOCK\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order_id").value(orderId))
            .andExpect(jsonPath("$.data.payment_params.callback_path").value("/api/callbacks/payments/wechat"));

        mockMvc.perform(post("/api/app/orders/{order_id}/cancel", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"close_reason\":\"用户取消\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payment_status").value("CLOSED"));

        mockMvc.perform(post("/api/app/orders/{order_id}/pay", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payment_channel\":\"MOCK\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        long timeoutOrderId = createOrder(studentToken, courseId, specId, null, 1, "s5-pending-timeout", 89900);
        jdbcTemplate.update("update trade_order set payment_expire_at = ? where id = ?", LocalDateTime.now().minusMinutes(1), timeoutOrderId);
        mockMvc.perform(post("/api/app/orders/{order_id}/pay", timeoutOrderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payment_channel\":\"MOCK\"}"))
            .andExpect(status().isConflict());
        assertThat(findString("trade_order", "payment_status", timeoutOrderId)).isEqualTo("CLOSED");
    }

    @Test
    void should_mock_pay_through_callback_and_create_success_side_effects_once() throws Exception {
        long courseId = seedCourse("S5 支付成功课程", true, 199900);
        long specId = findSpecId(courseId);
        long addressId = seedAddress(100000000302L);
        mockMvc.perform(post("/api/h5/lead/leads")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"S5 支付线索","mobile":"13900000009","source_code":"S5_PAY","intent_course_id":%d}
                    """.formatted(courseId)))
            .andExpect(status().isCreated());
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long orderId = createOrder(studentToken, courseId, specId, addressId, 1, "s5-pay-success", 199900);

        String callbackPayload = """
            {
              "event_no": "S5_PAY_SUCCESS_EVENT",
              "external_payment_no": "MOCKPAY-S5-SUCCESS",
              "paid_amount_cent": 199900,
              "payment_result": "SUCCESS",
              "paid_at": "2026-05-14T15:00:00",
              "raw_snapshot": {"scenario":"success"}
            }
            """;
        mockMvc.perform(post("/api/app/orders/{order_id}/mock-pay", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(callbackPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payment_status").value("PAID"));

        mockMvc.perform(post("/api/app/orders/{order_id}/mock-pay", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(callbackPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.payment_status").value("PAID"));

        assertThat(findString("trade_order", "payment_status", orderId)).isEqualTo("PAID");
        assertThat(countRows("pay_payment", "order_id", orderId)).isEqualTo(1);
        assertThat(countRows("integration_callback_event", "order_id", orderId)).isEqualTo(1);
        assertThat(countRows("learning_entitlement", "order_id", orderId)).isEqualTo(1);
        assertThat(countRows("fulfillment_shipment", "order_id", orderId)).isEqualTo(1);
        assertThat(countRows("notify_message", "order_id", orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from crm_lead where mobile = '13900000009' and converted_order_id = ?",
                Integer.class,
                orderId)).isEqualTo(1);
        assertThat(documentTypes(orderId)).contains("PAYMENT", "ENTITLEMENT", "SHIPMENT", "NOTIFICATION");
    }

    @Test
    void should_keep_order_pending_when_callback_amount_mismatch() throws Exception {
        long courseId = seedCourse("S5 金额不一致课程", false, 49900);
        long specId = findSpecId(courseId);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long orderId = createOrder(studentToken, courseId, specId, null, 1, "s5-amount-mismatch", 49900);
        String merchantOrderNo = findString("trade_order", "merchant_order_no", orderId);

        mockMvc.perform(post("/api/callbacks/payments/wechat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "event_no": "S5_AMOUNT_MISMATCH_EVENT",
                      "merchant_order_no": "%s",
                      "external_payment_no": "MOCKPAY-S5-MISMATCH",
                      "paid_amount_cent": 49901,
                      "payment_result": "SUCCESS",
                      "paid_at": "2026-05-14T15:10:00",
                      "raw_snapshot": {"scenario":"amount_mismatch"}
                    }
                    """.formatted(merchantOrderNo)))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.data.processing_status").value("FAILED"));

        assertThat(findString("trade_order", "payment_status", orderId)).isEqualTo("PENDING");
        assertThat(countRows("pay_payment", "order_id", orderId)).isEqualTo(0);
        assertThat(countRows("learning_entitlement", "order_id", orderId)).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
                "select failure_reason from integration_callback_event where order_id = ?",
                String.class,
                orderId)).contains("金额");
    }

    @Test
    void should_query_app_and_admin_orders_with_document_links_and_admin_guard() throws Exception {
        long courseId = seedCourse("S5 查询单据链课程", false, 59900);
        long specId = findSpecId(courseId);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long orderId = createOrder(studentToken, courseId, specId, null, 1, "s5-query", 59900);
        payOrder(studentToken, orderId, 59900, "S5_QUERY_PAY_EVENT", "MOCKPAY-S5-QUERY");

        mockMvc.perform(get("/api/app/orders")
                .header("Authorization", "Bearer " + studentToken)
                .param("payment_status", "PAID"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].order_id").value(orderId))
            .andExpect(jsonPath("$.data.records[*].payment_status", hasItem("PAID")));

        mockMvc.perform(get("/api/app/orders/{order_id}", orderId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order_id").value(orderId))
            .andExpect(jsonPath("$.data.document_links", hasSize(3)))
            .andExpect(jsonPath("$.data.document_links[*].document_type", hasItem("PAYMENT")))
            .andExpect(jsonPath("$.data.document_links[*].document_type", hasItem("ENTITLEMENT")))
            .andExpect(jsonPath("$.data.document_links[*].document_type", hasItem("NOTIFICATION")));

        mockMvc.perform(get("/api/admin/orders/{order_id}", orderId))
            .andExpect(status().isUnauthorized());

        String adminToken = adminLogin("DEMO_SERVICE");
        mockMvc.perform(get("/api/admin/orders/{order_id}", orderId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order_id").value(orderId))
            .andExpect(jsonPath("$.data.payment_records", hasSize(1)))
            .andExpect(jsonPath("$.data.entitlements", hasSize(1)))
            .andExpect(jsonPath("$.data.document_links[*].document_type", hasItem("PAYMENT")));
    }

    @Test
    void should_block_unbound_student_on_app_order_cancel_list_and_detail() throws Exception {
        long courseId = seedCourse("S5 订单接口手机号校验课程", false, 69900);
        long specId = findSpecId(courseId);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long orderId = createOrder(studentToken, courseId, specId, null, 1, "s5-unbound-order-ops", 69900);
        jdbcTemplate.update("update edu_student set mobile = null, updated_at = now() where student_no = 'STU_S3_DEMO'");

        mockMvc.perform(get("/api/app/orders")
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));

        mockMvc.perform(get("/api/app/orders/{order_id}", orderId)
                .header("Authorization", "Bearer " + studentToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));

        mockMvc.perform(post("/api/app/orders/{order_id}/cancel", orderId)
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"close_reason\":\"手机号未授权阻断\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUSINESS_RULE_BLOCKED"));
    }

    @Test
    void should_limit_warehouse_admin_to_paid_physical_orders() throws Exception {
        long courseId = seedCourse("S5 仓管数据范围课程", true, 79900);
        long specId = findSpecId(courseId);
        long addressId = seedAddress(100000000302L);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long orderId = createOrder(studentToken, courseId, specId, addressId, 1, "s5-warehouse-scope", 79900);
        String orderNo = findString("trade_order", "order_no", orderId);
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");

        mockMvc.perform(get("/api/admin/orders")
                .header("Authorization", "Bearer " + warehouseToken)
                .param("keyword", orderNo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records", hasSize(0)));

        mockMvc.perform(get("/api/admin/orders/{order_id}", orderId)
                .header("Authorization", "Bearer " + warehouseToken))
            .andExpect(status().isForbidden());

        payOrder(studentToken, orderId, 79900, "S5_WAREHOUSE_SCOPE_PAY_EVENT", "MOCKPAY-S5-WAREHOUSE");

        mockMvc.perform(get("/api/admin/orders")
                .header("Authorization", "Bearer " + warehouseToken)
                .param("keyword", orderNo))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].order_id").value(orderId))
            .andExpect(jsonPath("$.data.records[0].payment_status").value("PAID"));

        mockMvc.perform(get("/api/admin/orders/{order_id}", orderId)
                .header("Authorization", "Bearer " + warehouseToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.order_id").value(orderId))
            .andExpect(jsonPath("$.data.shipments", hasSize(1)));
    }

    @Test
    void should_filter_admin_orders_by_documented_paid_at_range() throws Exception {
        long courseId = seedCourse("S5 支付时间筛选课程", false, 80900);
        long specId = findSpecId(courseId);
        String studentToken = appLogin("DEMO_APP_STUDENT");
        long targetOrderId = createOrder(studentToken, courseId, specId, null, 1, "s5-paid-range-target", 80900);
        long otherOrderId = createOrder(studentToken, courseId, specId, null, 1, "s5-paid-range-other", 80900);
        payOrder(studentToken, targetOrderId, 80900, "S5_RANGE_TARGET_PAY", "MOCKPAY-S5-RANGE-TARGET");
        payOrder(studentToken, otherOrderId, 80900, "S5_RANGE_OTHER_PAY", "MOCKPAY-S5-RANGE-OTHER");
        jdbcTemplate.update("update trade_order set paid_at = ? where id = ?", LocalDateTime.parse("2026-05-10T10:00:00"), targetOrderId);
        jdbcTemplate.update("update trade_order set paid_at = ? where id = ?", LocalDateTime.parse("2026-05-12T10:00:00"), otherOrderId);
        String targetOrderNo = findString("trade_order", "order_no", targetOrderId);
        String otherOrderNo = findString("trade_order", "order_no", otherOrderId);
        String adminToken = adminLogin("DEMO_SERVICE");

        mockMvc.perform(get("/api/admin/orders")
                .header("Authorization", "Bearer " + adminToken)
                .param("paid_at_start", "2026-05-10T00:00:00")
                .param("paid_at_end", "2026-05-10T23:59:59"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[*].order_no", hasItem(targetOrderNo)))
            .andExpect(jsonPath("$.data.records[*].order_no", not(hasItem(otherOrderNo))));
    }

    private long seedCourse(String title, boolean containsPhysical, long salePriceCent) {
        long suffix = SEQUENCE.incrementAndGet();
        long courseId = 200000000000L + suffix;
        long specId = 200000100000L + suffix;
        jdbcTemplate.update(
            """
            insert into course (
                id, course_no, course_title, course_type, summary, teacher_user_id,
                category_code, default_tax_rule_id, status, sale_start_at, sale_end_at,
                published_at, created_by, updated_by
            ) values (?, ?, ?, 'LIVE', 'S5 test course', 100000000008, 'S5', 100000000401,
                'ON_SHELF', ?, ?, ?, 0, 0)
            """,
            courseId,
            "S5C" + courseId,
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
            "S5S" + specId,
            courseId,
            salePriceCent,
            salePriceCent + 10000,
            containsPhysical ? 1 : 0,
            containsPhysical ? 100000000402L : null,
            containsPhysical ? 100000000403L : null,
            "{\"training_amount_cent\":" + salePriceCent + "}");
        return courseId;
    }

    private long seedAddress(long studentId) {
        long addressId = 200000200000L + SEQUENCE.incrementAndGet();
        jdbcTemplate.update(
            """
            insert into student_address (
                id, student_id, receiver_name, receiver_mobile, province, city, district,
                detail_address, postal_code, is_default, status, created_by, updated_by
            ) values (?, ?, 'S5 收货人', '13900000009', '浙江省', '杭州市', '西湖区',
                'S5 测试路 1 号', '310000', 1, 'ACTIVE', 0, 0)
            """,
            addressId,
            studentId);
        return addressId;
    }

    private long findSpecId(long courseId) {
        return jdbcTemplate.queryForObject("select id from course_spec where course_id = ?", Long.class, courseId);
    }

    private String confirm(String token, long courseId, long specId, Long addressId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/app/orders/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": %d,
                      "address_id": %s,
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S5_TEST"
                    }
                    """.formatted(courseId, specId, quantity, addressId == null ? "null" : addressId.toString())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private long createOrder(
        String token,
        long courseId,
        long specId,
        Long addressId,
        int quantity,
        String requestNo,
        long payableAmountCent
    ) throws Exception {
        String confirmResponse = confirm(token, courseId, specId, addressId, quantity);
        String confirmToken = extractString(confirmResponse, "confirm_token");
        String response = mockMvc.perform(post("/api/app/orders")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", requestNo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "course_id": %d,
                      "spec_id": %d,
                      "quantity": %d,
                      "address_id": %s,
                      "client_request_no": "%s",
                      "source_channel": "MINIPROGRAM",
                      "source_code": "S5_TEST",
                      "confirmed_payable_amount_cent": %d,
                      "confirm_token": "%s"
                    }
                    """.formatted(
                        courseId,
                        specId,
                        quantity,
                        addressId == null ? "null" : addressId.toString(),
                        requestNo,
                        payableAmountCent,
                        confirmToken)))
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
                      "raw_snapshot": {"scenario":"query"}
                    }
                    """.formatted(eventNo, externalPaymentNo, amountCent)))
            .andExpect(status().isOk());
    }

    private String appLogin(String wxCode) throws Exception {
        String response = mockMvc.perform(post("/api/app/auth/wechat-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"wx_code":"mock:%s","source_channel":"S5_TEST"}
                    """.formatted(wxCode)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "access_token");
    }

    private String adminLogin(String userNo) throws Exception {
        String response = mockMvc.perform(post("/api/admin/auth/test-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"user_no":"%s"}
                    """.formatted(userNo)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return extractString(response, "access_token");
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

    @SuppressWarnings("unused")
    private Map<String, Object> findOrder(long orderId) {
        return jdbcTemplate.queryForMap("select * from trade_order where id = ?", orderId);
    }
}
