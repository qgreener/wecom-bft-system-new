package com.wecombft.domain.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wecombft.domain.model.MoneyCent;

class TaxAmountCalculatorTest {

    @Test
    void should_calculate_inclusive_tax_by_cent_without_floating_point() {
        MoneyCent tax = TaxAmountCalculator.inclusiveTax(MoneyCent.of(10_600L), new BigDecimal("0.0600"));

        assertThat(tax.cent()).isEqualTo(600L);
    }

    @Test
    void should_round_single_line_tax_to_cent() {
        MoneyCent tax = TaxAmountCalculator.inclusiveTax(MoneyCent.of(19_900L), new BigDecimal("0.0600"));

        assertThat(tax.cent()).isEqualTo(1_126L);
    }

    @Test
    void should_sum_tax_by_invoice_lines() {
        MoneyCent tax = TaxAmountCalculator.sumLineTaxes(List.of(
                new TaxAmountCalculator.TaxLine(MoneyCent.of(10_600L), new BigDecimal("0.0600")),
                new TaxAmountCalculator.TaxLine(MoneyCent.of(5_300L), new BigDecimal("0.0600"))));

        assertThat(tax.cent()).isEqualTo(900L);
    }

    @Test
    void should_reject_negative_tax_rate() {
        assertThatThrownBy(() -> TaxAmountCalculator.inclusiveTax(MoneyCent.of(100L), new BigDecimal("-0.0100")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tax rate");
    }
}
