package com.wecombft.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void should_build_stable_key_from_business_parts() {
        IdempotencyKey key = IdempotencyKey.ofParts("MCH202605140001", "PAY202605140001");

        assertThat(key.value()).isEqualTo("MCH202605140001:PAY202605140001");
    }

    @Test
    void should_reject_blank_or_oversized_key() {
        assertThatThrownBy(() -> IdempotencyKey.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotency");

        String oversized = "a".repeat(129);
        assertThatThrownBy(() -> IdempotencyKey.of(oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128");
    }

    @Test
    void should_reject_blank_key_part() {
        assertThatThrownBy(() -> IdempotencyKey.ofParts("order-no", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("part");
    }
}
