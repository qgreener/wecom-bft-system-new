package com.wecombft.domain.service.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import com.wecombft.domain.model.MoneyCent;

public final class TaxAmountCalculator {

    private static final BigDecimal ONE = BigDecimal.ONE;

    private TaxAmountCalculator() {
    }

    public static MoneyCent inclusiveTax(MoneyCent grossAmount, BigDecimal taxRate) {
        Objects.requireNonNull(grossAmount, "grossAmount must not be null");
        validateTaxRate(taxRate);
        if (grossAmount.cent() == 0L || taxRate.compareTo(BigDecimal.ZERO) == 0) {
            return MoneyCent.zero();
        }

        BigDecimal grossCent = BigDecimal.valueOf(grossAmount.cent());
        BigDecimal taxCent = grossCent
                .multiply(taxRate)
                .divide(ONE.add(taxRate), 0, RoundingMode.HALF_UP);
        return MoneyCent.of(taxCent.longValueExact());
    }

    public static MoneyCent sumLineTaxes(List<TaxLine> taxLines) {
        Objects.requireNonNull(taxLines, "taxLines must not be null");
        MoneyCent total = MoneyCent.zero();
        for (TaxLine line : taxLines) {
            total = total.add(inclusiveTax(line.grossAmount(), line.taxRate()));
        }
        return total;
    }

    private static void validateTaxRate(BigDecimal taxRate) {
        Objects.requireNonNull(taxRate, "tax rate must not be null");
        if (taxRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("tax rate cannot be negative");
        }
    }

    public record TaxLine(MoneyCent grossAmount, BigDecimal taxRate) {
        public TaxLine {
            Objects.requireNonNull(grossAmount, "grossAmount must not be null");
            validateTaxRate(taxRate);
        }
    }
}
