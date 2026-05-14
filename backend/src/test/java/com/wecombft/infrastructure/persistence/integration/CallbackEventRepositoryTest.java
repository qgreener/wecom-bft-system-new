package com.wecombft.infrastructure.persistence.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CallbackEventRepositoryTest {

    @Autowired
    private CallbackEventRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_record_external_callback_once_by_source_event_no() {
        CallbackEventRecord first = repository.recordReceived(new CallbackEventRepository.CallbackEventCommand(
                3000000000000000001L,
                "PAY_EVT_001",
                "WECHAT_PAY",
                "PAYMENT_SUCCESS",
                "MCH001:PAY_EVT_001",
                3000000000000000101L,
                "PAY_PAYMENT",
                3000000000000000201L,
                "PAYMENT_DEMO_001",
                "{\"amount_cent\":10600}"));

        CallbackEventRecord duplicate = repository.recordReceived(new CallbackEventRepository.CallbackEventCommand(
                3000000000000000002L,
                "PAY_EVT_001",
                "WECHAT_PAY",
                "PAYMENT_SUCCESS",
                "MCH001:PAY_EVT_001",
                3000000000000000101L,
                "PAY_PAYMENT",
                3000000000000000201L,
                "PAYMENT_DEMO_001",
                "{\"amount_cent\":10600}"));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(countBySourceEvent("WECHAT_PAY", "PAY_EVT_001")).isEqualTo(1);
        assertThat(duplicate.processingStatus()).isEqualTo("PENDING");
    }

    @Test
    void should_mark_callback_event_processed_or_failed_without_deleting_it() {
        CallbackEventRecord event = repository.recordReceived(new CallbackEventRepository.CallbackEventCommand(
                3000000000000000003L,
                "PAY_EVT_002",
                "WECHAT_PAY",
                "PAYMENT_SUCCESS",
                "MCH001:PAY_EVT_002",
                null,
                null,
                null,
                null,
                "{}"));

        repository.markFailed(event.id(), "amount mismatch");
        CallbackEventRecord failed = repository.findBySourceAndEventNo("WECHAT_PAY", "PAY_EVT_002").orElseThrow();
        assertThat(failed.processingStatus()).isEqualTo("FAILED");
        assertThat(failed.retryCount()).isEqualTo(1);

        repository.markProcessed(event.id());
        CallbackEventRecord processed = repository.findBySourceAndEventNo("WECHAT_PAY", "PAY_EVT_002").orElseThrow();
        assertThat(processed.processingStatus()).isEqualTo("PROCESSED");
        assertThat(processed.processedAt()).isNotNull();
    }

    private Integer countBySourceEvent(String sourceSystem, String eventNo) {
        return jdbcTemplate.queryForObject(
                """
                select count(*) from integration_callback_event
                where source_system = ? and event_no = ?
                """,
                Integer.class,
                sourceSystem,
                eventNo);
    }
}
