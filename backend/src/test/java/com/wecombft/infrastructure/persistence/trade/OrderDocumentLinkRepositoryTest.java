package com.wecombft.infrastructure.persistence.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class OrderDocumentLinkRepositoryTest {

    @Autowired
    private OrderDocumentLinkRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void should_upsert_order_document_link_by_order_and_document() {
        OrderDocumentLinkRecord first = repository.upsert(new OrderDocumentLinkRepository.DocumentLinkCommand(
                3000000000000001001L,
                3000000000000001101L,
                "ORDER_DEMO_001",
                "PAYMENT",
                3000000000000001201L,
                "PAYMENT_DEMO_001",
                "PENDING",
                10_600L,
                "DIRECT",
                LocalDateTime.of(2026, 5, 14, 19, 30),
                "pay_payment",
                "created",
                0L));

        OrderDocumentLinkRecord updated = repository.upsert(new OrderDocumentLinkRepository.DocumentLinkCommand(
                3000000000000001002L,
                3000000000000001101L,
                "ORDER_DEMO_001",
                "PAYMENT",
                3000000000000001201L,
                "PAYMENT_DEMO_001",
                "SUCCESS",
                10_600L,
                "DIRECT",
                LocalDateTime.of(2026, 5, 14, 19, 31),
                "pay_payment",
                "status updated",
                0L));

        assertThat(updated.id()).isEqualTo(first.id());
        assertThat(updated.documentStatus()).isEqualTo("SUCCESS");
        assertThat(countByOrder(3000000000000001101L)).isEqualTo(1);
    }

    @Test
    void should_rebuild_order_document_links_by_upserting_each_source_record() {
        repository.upsertAll(List.of(
                new OrderDocumentLinkRepository.DocumentLinkCommand(
                        3000000000000001003L,
                        3000000000000001102L,
                        "ORDER_DEMO_002",
                        "PAYMENT",
                        3000000000000001202L,
                        "PAYMENT_DEMO_002",
                        "SUCCESS",
                        20_000L,
                        "DIRECT",
                        LocalDateTime.of(2026, 5, 14, 19, 32),
                        "pay_payment",
                        null,
                        0L),
                new OrderDocumentLinkRepository.DocumentLinkCommand(
                        3000000000000001004L,
                        3000000000000001102L,
                        "ORDER_DEMO_002",
                        "ENTITLEMENT",
                        3000000000000001302L,
                        "ENTITLEMENT_DEMO_002",
                        "ACTIVE",
                        null,
                        "SIDE_EFFECT",
                        LocalDateTime.of(2026, 5, 14, 19, 33),
                        "learning_entitlement",
                        null,
                        0L)));

        assertThat(repository.findByOrderId(3000000000000001102L))
                .extracting(OrderDocumentLinkRecord::documentType)
                .containsExactly("PAYMENT", "ENTITLEMENT");
    }

    private Integer countByOrder(long orderId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from order_document_link where order_id = ?",
                Integer.class,
                orderId);
    }
}
