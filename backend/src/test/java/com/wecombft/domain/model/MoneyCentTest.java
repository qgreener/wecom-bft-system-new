package com.wecombft.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyCentTest {

    @Test
    void should_store_money_as_non_negative_cent_value() {
        MoneyCent money = MoneyCent.of(12_345L);

        assertThat(money.cent()).isEqualTo(12_345L);
        assertThatThrownBy(() -> MoneyCent.of(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cent");
    }

    @Test
    void should_calculate_cent_amount_without_floating_point() {
        MoneyCent result = MoneyCent.of(2_000L)
                .add(MoneyCent.of(345L))
                .subtract(MoneyCent.of(45L))
                .multiply(2);

        assertThat(result.cent()).isEqualTo(4_600L);
    }

    @Test
    void should_reject_negative_calculation_result() {
        assertThatThrownBy(() -> MoneyCent.of(100L).subtract(MoneyCent.of(101L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }
}
