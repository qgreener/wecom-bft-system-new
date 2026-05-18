package com.wecombft.interfaces.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.infrastructure.integration.wecom.crypto.WXBizMsgCrypt;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WecomCallbackControllerTest {

    private static final String TOKEN = "TEST_CALLBACK_TOKEN";
    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLM1234";
    private static final String CORP_ID = "TEST_CORP_ID";
    private static final String RANDOM_16 = "1234567890abcdef";

    private static final AtomicLong SEQUENCE = new AtomicLong(8_700_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void get_verifyUrl_returnsDecryptedPlainText() throws Exception {
        String plain = "1616140317555161061";
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, plain, RANDOM_16);
        String timestamp = "1700000000";
        String nonce = "GETnonce";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);

        MvcResult result = mockMvc.perform(get("/api/callbacks/wecom/receive")
                .param("msg_signature", signature)
                .param("timestamp", timestamp)
                .param("nonce", nonce)
                .param("echostr", encrypted))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEqualTo(plain);
    }

    @Test
    void get_verifyUrl_signatureMismatch_returns401() throws Exception {
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, "any", RANDOM_16);

        mockMvc.perform(get("/api/callbacks/wecom/receive")
                .param("msg_signature", "deadbeef0000000000000000000000000000beef")
                .param("timestamp", "1700000000")
                .param("nonce", "n1")
                .param("echostr", encrypted))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void post_approvalChange_flipsApprovalToApproved() throws Exception {
        long approvalId = nextId();
        String spNo = "TESTSPNO" + approvalId;
        insertPendingRoleApprovalWithWecomId(approvalId, spNo);
        String xml = approvalChangeXml(spNo, "2");
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, xml, RANDOM_16);
        String timestamp = "1700000001";
        String nonce = "POSTnonce";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);
        String body = wrapEncryptEnvelope(encrypted);

        mockMvc.perform(post("/api/callbacks/wecom/receive")
                .param("msg_signature", signature)
                .param("timestamp", timestamp)
                .param("nonce", nonce)
                .contentType(MediaType.APPLICATION_XML)
                .content(body))
            .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
            "select status from approval_record where id = ?", String.class, approvalId);
        assertThat(status).isEqualTo("APPROVED");
    }

    @Test
    void post_approvalChange_alreadyTerminal_idempotentReturns200() throws Exception {
        long approvalId = nextId();
        String spNo = "TESTSPNO" + approvalId;
        insertPendingRoleApprovalWithWecomId(approvalId, spNo);
        // 手动设为终态以模拟重复推送
        jdbcTemplate.update("update approval_record set status='APPROVED' where id=?", approvalId);

        String xml = approvalChangeXml(spNo, "2");
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, xml, RANDOM_16);
        String timestamp = "1700000002";
        String nonce = "IDEMnonce";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);
        String body = wrapEncryptEnvelope(encrypted);

        mockMvc.perform(post("/api/callbacks/wecom/receive")
                .param("msg_signature", signature)
                .param("timestamp", timestamp)
                .param("nonce", nonce)
                .contentType(MediaType.APPLICATION_XML)
                .content(body))
            .andExpect(status().isOk());

        String status = jdbcTemplate.queryForObject(
            "select status from approval_record where id = ?", String.class, approvalId);
        assertThat(status).isEqualTo("APPROVED");
    }

    @Test
    void post_unknownSpNo_returns200WithoutSideEffect() throws Exception {
        String xml = approvalChangeXml("NONEXISTENT_SP_NO_12345", "2");
        String encrypted = WXBizMsgCrypt.encrypt(AES_KEY, CORP_ID, xml, RANDOM_16);
        String timestamp = "1700000003";
        String nonce = "UNKnonce";
        String signature = WXBizMsgCrypt.sha1Signature(TOKEN, timestamp, nonce, encrypted);
        String body = wrapEncryptEnvelope(encrypted);

        mockMvc.perform(post("/api/callbacks/wecom/receive")
                .param("msg_signature", signature)
                .param("timestamp", timestamp)
                .param("nonce", nonce)
                .contentType(MediaType.APPLICATION_XML)
                .content(body))
            .andExpect(status().isOk());
    }

    private void insertPendingRoleApprovalWithWecomId(long approvalId, String spNo) {
        long applicantUserId = jdbcTemplate.queryForObject(
            "select id from sys_user where user_no='DEMO_UNASSIGNED'", Long.class);
        long roleId = jdbcTemplate.queryForObject(
            "select id from sys_role where role_code='EDU_ADMIN'", Long.class);
        jdbcTemplate.update(
            """
            insert into approval_record (
                id, approval_no, approval_type, title, applicant_user_id,
                related_object_type, related_object_id, related_object_no, status, submit_reason,
                submitted_at, wecom_approval_id, created_by, updated_by
            ) values (?, ?, 'ROLE_APPLICATION', ?, ?, 'SYS_ROLE', ?, 'EDU_ADMIN',
                'PENDING', ?, ?, ?, ?, ?)
            """,
            approvalId, "APRTEST" + approvalId,
            "测试角色申请", applicantUserId, roleId, "callback test", LocalDateTime.now(),
            spNo, applicantUserId, applicantUserId);
    }

    private String approvalChangeXml(String spNo, String spStatus) {
        return "<xml>"
            + "<ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
            + "<MsgType><![CDATA[event]]></MsgType>"
            + "<Event><![CDATA[sys_approval_change]]></Event>"
            + "<ApprovalInfo>"
            + "<SpNo><![CDATA[" + spNo + "]]></SpNo>"
            + "<SpStatus>" + spStatus + "</SpStatus>"
            + "<Speech><![CDATA[callback test approval]]></Speech>"
            + "</ApprovalInfo>"
            + "</xml>";
    }

    private String wrapEncryptEnvelope(String encrypted) {
        return "<xml><ToUserName><![CDATA[" + CORP_ID + "]]></ToUserName>"
            + "<Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>";
    }

    private long nextId() {
        return SEQUENCE.getAndIncrement();
    }
}
