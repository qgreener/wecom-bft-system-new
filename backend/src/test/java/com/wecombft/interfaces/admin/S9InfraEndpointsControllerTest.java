package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class S9InfraEndpointsControllerTest {

    private static final AtomicLong SEQUENCE = new AtomicLong(9_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void should_upload_and_download_file_end_to_end() throws Exception {
        String accountingToken = adminLogin("DEMO_ACCOUNTING");
        MockMultipartFile file = new MockMultipartFile(
            "file", "demo.pdf", "application/pdf",
            "S9 demo file payload".getBytes());

        String response = mockMvc.perform(multipart("/api/admin/files")
                .file(file)
                .param("biz_type", "INVOICE")
                .param("biz_id", "9001")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s9-file-upload-1"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.file_no").exists())
            .andExpect(jsonPath("$.data.file_size").value(20))
            .andReturn().getResponse().getContentAsString();

        String fileNo = extractString(response, "file_no");

        // Second upload with same digest+biz returns existing
        mockMvc.perform(multipart("/api/admin/files")
                .file(new MockMultipartFile("file", "demo.pdf", "application/pdf",
                    "S9 demo file payload".getBytes()))
                .param("biz_type", "INVOICE")
                .param("biz_id", "9001")
                .header("Authorization", "Bearer " + accountingToken)
                .header("Idempotency-Key", "s9-file-upload-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.file_no").value(fileNo));

        // Download returns bytes
        mockMvc.perform(get("/api/admin/files/{file_no}/download", fileNo)
                .header("Authorization", "Bearer " + accountingToken)
                .param("download_reason", "演示下载"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-File-No", fileNo))
            .andExpect(header().exists("Content-Disposition"));
    }

    @Test
    void should_reject_file_without_business_permission() throws Exception {
        String warehouseToken = adminLogin("DEMO_WAREHOUSE");
        MockMultipartFile file = new MockMultipartFile(
            "file", "invoice.pdf", "application/pdf", "INVOICE bytes".getBytes());
        mockMvc.perform(multipart("/api/admin/files")
                .file(file)
                .param("biz_type", "INVOICE")
                .param("biz_id", "9002")
                .header("Authorization", "Bearer " + warehouseToken)
                .header("Idempotency-Key", "s9-file-upload-wh"))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_list_mock_scenes_and_filter_by_capability() throws Exception {
        String superToken = adminLogin("DEMO_ADMIN");
        mockMvc.perform(get("/api/admin/mock/scenes")
                .header("Authorization", "Bearer " + superToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenes[*].scene_code").exists());

        mockMvc.perform(get("/api/admin/mock/scenes")
                .header("Authorization", "Bearer " + superToken)
                .param("capability", "REFUND"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.scenes[?(@.capability != 'REFUND')]").isEmpty());
    }

    @Test
    void should_reject_mock_scenes_for_non_super_admin() throws Exception {
        String teacherToken = adminLogin("DEMO_TEACHER");
        mockMvc.perform(get("/api/admin/mock/scenes")
                .header("Authorization", "Bearer " + teacherToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void should_close_compensation_via_actions_endpoint() throws Exception {
        String serviceToken = adminLogin("DEMO_SERVICE");
        long refundId = seedFailedRefund();

        // CLOSE action: writes failure_reason without restarting flow
        mockMvc.perform(post("/api/collab/compensations/{task_id}/actions", refundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s9-comp-close-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"compensation_type":"REFUND","action":"CLOSE","closed_reason":"线下处理已完成","remark":"S9 关闭"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.processing_status").value("CLOSED"));

        String reason = jdbcTemplate.queryForObject(
            "select failure_reason from pay_refund where id = ?", String.class, refundId);
        assertThat(reason).isEqualTo("线下处理已完成");

        // Missing closed_reason should 400
        long anotherRefundId = seedFailedRefund();
        mockMvc.perform(post("/api/collab/compensations/{task_id}/actions", anotherRefundId)
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s9-comp-close-noreason")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"compensation_type":"REFUND","action":"CLOSE"}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_404_for_deleted_compensation_retry_path() throws Exception {
        String serviceToken = adminLogin("DEMO_SERVICE");
        mockMvc.perform(post("/api/collab/compensations/123/retry")
                .header("Authorization", "Bearer " + serviceToken)
                .header("Idempotency-Key", "s9-deleted-retry")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"compensation_type\":\"REFUND\",\"action\":\"MANUAL_REQUIRED\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_404_for_deleted_split_approval_endpoints() throws Exception {
        String superToken = adminLogin("DEMO_ADMIN");
        mockMvc.perform(post("/api/collab/course-approvals/123/actions")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/collab/purchase-approvals/123/actions")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void should_dispatch_approval_actions_by_type() throws Exception {
        String superToken = adminLogin("DEMO_ADMIN");
        long roleApprovalId = seedRoleApprovalPending();

        mockMvc.perform(post("/api/collab/approvals/{approval_id}/actions", roleApprovalId)
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"action":"REJECT","approval_comment":"S9 测试拒绝"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.related_object_type").value("SYS_ROLE"));

        // Unknown approval_type
        long unknownApprovalId = seedApprovalPending("UNKNOWN_TYPE", "UNKNOWN_OBJ", 999L);
        mockMvc.perform(post("/api/collab/approvals/{approval_id}/actions", unknownApprovalId)
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
            .andExpect(status().isBadRequest());

        // 404 if approval not found
        mockMvc.perform(post("/api/collab/approvals/999999999/actions")
                .header("Authorization", "Bearer " + superToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}"))
            .andExpect(status().isNotFound());
    }

    // ---- seed helpers ----

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
            values (?, ?, ?, ?, ?, 'S9', 10000, 10000, 10000, 'PAID', 'NO_SHIPMENT', 'FAILED',
                    'NOT_APPLIED', ?, '{}', '{}', '{}', 0, 0)
            """,
            orderId, "S9_TO_" + orderId, "S9_MOR_" + orderId, studentId, userId,
            LocalDateTime.now().plusHours(1));
        jdbcTemplate.update(
            """
            insert into pay_refund (id, refund_no, order_id, order_no, payment_id, student_id,
                apply_amount_cent, refund_reason, status, refund_channel, entitlement_action,
                failure_reason, idempotency_key, created_by, updated_by)
            values (?, ?, ?, ?, ?, ?, 10000, 'S9 failed', 'FAILED', 'ORIGINAL', 'FREEZE',
                    'Mock 失败', ?, 0, 0)
            """,
            refundId, "S9_RFFAIL_" + refundId, orderId, "S9_TO_" + orderId, paymentId, studentId,
            "S9_RFIDEM_" + refundId);
        return refundId;
    }

    private long seedRoleApprovalPending() {
        return seedApprovalPending("ROLE_APPLICATION", "SYS_ROLE", 100000000003L);
    }

    private long seedApprovalPending(String approvalType, String relatedType, long relatedId) {
        long approvalId = SEQUENCE.incrementAndGet();
        long applicantUserId = 100000000003L; // DEMO_SERVICE
        jdbcTemplate.update(
            """
            insert into approval_record (
                id, approval_no, approval_type, title, applicant_user_id,
                related_object_type, related_object_id, related_object_no, status,
                submit_reason, submitted_at, created_by, updated_by
            ) values (?, ?, ?, 'S9 测试审批', ?, ?, ?, ?, 'PENDING', 'S9 提交', ?, ?, ?)
            """,
            approvalId, "S9_APR_" + approvalId, approvalType, applicantUserId,
            relatedType, relatedId, "S9_OBJ_" + approvalId,
            LocalDateTime.now(), applicantUserId, applicantUserId);
        return approvalId;
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
