package com.wecombft.application.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RequestIdempotencyGuardTest {

    private final RequestIdempotencyGuard guard = new RequestIdempotencyGuard();

    @Test
    void should_require_request_idempotency_header_for_mutation_entry() {
        assertThat(guard.requireKey(" order-001:pay-001 ").value()).isEqualTo("order-001:pay-001");

        assertThatThrownBy(() -> guard.requireKey(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency");
    }

    @Test
    void should_build_notification_key_from_business_event_parts() {
        assertThat(guard.compose("REFUND_SUCCESS", "PAY_REFUND", 9001L, "STUDENT_1001").value())
                .isEqualTo("REFUND_SUCCESS:PAY_REFUND:9001:STUDENT_1001");
    }
}
