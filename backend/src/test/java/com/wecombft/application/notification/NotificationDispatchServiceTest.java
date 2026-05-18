package com.wecombft.application.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wecombft.infrastructure.integration.wecom.WecomCardAdapter;
import com.wecombft.infrastructure.integration.wecom.WecomCardCommand;
import com.wecombft.infrastructure.integration.wecom.WecomCardResult;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationDispatchServiceTest {

    @Autowired private NotificationDispatchService dispatcher;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordingFakeCardAdapter fakeCardAdapter;

    @BeforeEach
    void resetFake() {
        fakeCardAdapter.next.set(WecomCardResult.success("fake_msg_1"));
    }

    @Test
    void dispatchToInternalUser_writesBothChannels_andMarksSuccessOnAdapterSuccess() {
        long demoAdminId = jdbcTemplate.queryForObject(
            "select id from sys_user where user_no='DEMO_ADMIN'", Long.class);

        dispatcher.dispatchToInternalUser(demoAdminId, new NotificationContent(
            "TEST_SCENE", "TEST_TPL", "标题", "正文",
            "TEST_OBJECT", 99001L, "TEST_KEY_1", demoAdminId,
            "https://finhub.tax/admin/#/dashboard"));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select channel, send_status from notify_message where idempotency_key like 'TEST_KEY_1%' order by channel");
        assertThat(rows).hasSize(2);
        Map<String, Object> inApp = rows.stream()
            .filter(r -> "IN_APP".equals(r.get("channel"))).findFirst().orElseThrow();
        Map<String, Object> wecom = rows.stream()
            .filter(r -> "WECOM_CARD".equals(r.get("channel"))).findFirst().orElseThrow();
        assertThat(inApp.get("send_status")).isEqualTo("PENDING");
        assertThat(wecom.get("send_status")).isEqualTo("SENT");
    }

    @Test
    void dispatchToInternalUser_adapterFails_marksWecomFailedButInAppStays() {
        fakeCardAdapter.next.set(WecomCardResult.failure("CHANNEL_DOWN"));
        long demoAdminId = jdbcTemplate.queryForObject(
            "select id from sys_user where user_no='DEMO_ADMIN'", Long.class);

        dispatcher.dispatchToInternalUser(demoAdminId, new NotificationContent(
            "TEST_SCENE", "TEST_TPL", "标题", "正文",
            "TEST_OBJECT", 99002L, "TEST_KEY_2", demoAdminId, null));

        Map<String, Object> wecom = jdbcTemplate.queryForMap(
            "select send_status, failure_reason from notify_message where idempotency_key='TEST_KEY_2:WECOM_CARD'");
        assertThat(wecom.get("send_status")).isEqualTo("FAILED");
        assertThat(wecom.get("failure_reason")).isEqualTo("CHANNEL_DOWN");
    }

    @Test
    void dispatchToInternalUser_receiverHasNoWecomBinding_marksFailed() {
        long unboundId = jdbcTemplate.queryForObject(
            "select id from sys_user where user_no='DEMO_UNASSIGNED'", Long.class);
        jdbcTemplate.update("update sys_user set wecom_user_id=null where id=?", unboundId);

        dispatcher.dispatchToInternalUser(unboundId, new NotificationContent(
            "TEST_SCENE", "TEST_TPL", "标题", "正文",
            "TEST_OBJECT", 99003L, "TEST_KEY_3", unboundId, null));

        Map<String, Object> wecom = jdbcTemplate.queryForMap(
            "select send_status, failure_reason from notify_message where idempotency_key='TEST_KEY_3:WECOM_CARD'");
        assertThat(wecom.get("send_status")).isEqualTo("FAILED");
        assertThat(wecom.get("failure_reason")).isEqualTo("RECEIVER_HAS_NO_WECOM_BINDING");
    }

    @Test
    void dispatchToStudent_writesInAppOnly() {
        Long studentId = jdbcTemplate.queryForObject(
            "select id from edu_student limit 1", Long.class);

        dispatcher.dispatchToStudent(studentId, new NotificationContent(
            "PAYMENT_SUCCESS", "PAYMENT_SUCCESS_TPL", "支付成功", "你的订单已支付",
            "TRADE_ORDER", 88001L, "TEST_STU_KEY_1", null, null));

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "select channel, receiver_student_id from notify_message where idempotency_key like 'TEST_STU_KEY_1%'");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("channel")).isEqualTo("IN_APP");
        assertThat(((Number) rows.get(0).get("receiver_student_id")).longValue()).isEqualTo(studentId);
    }

    @TestConfiguration
    static class FakeAdapterConfig {
        @Bean @Primary
        RecordingFakeCardAdapter fakeCardAdapter() {
            return new RecordingFakeCardAdapter();
        }
    }

    static class RecordingFakeCardAdapter implements WecomCardAdapter {
        final AtomicReference<WecomCardResult> next = new AtomicReference<>(WecomCardResult.success("fake"));

        @Override
        public WecomCardResult sendCard(WecomCardCommand command) {
            return next.get();
        }
    }
}
