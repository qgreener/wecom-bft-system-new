package com.wecombft.domain.model;

import java.util.Objects;

public record MoneyCent(long cent) {

    public MoneyCent {
        if (cent < 0) {
            throw new IllegalArgumentException("Money cent value cannot be negative");
        }
    }

    public static MoneyCent of(long cent) {
        return new MoneyCent(cent);
    }

    public static MoneyCent zero() {
        return new MoneyCent(0);
    }

    public MoneyCent add(MoneyCent other) {
        Objects.requireNonNull(other, "other money must not be null");
        return new MoneyCent(Math.addExact(cent, other.cent));
    }

    public MoneyCent subtract(MoneyCent other) {
        Objects.requireNonNull(other, "other money must not be null");
        long result = Math.subtractExact(cent, other.cent);
        if (result < 0) {
            throw new IllegalArgumentException("Money cent calculation result cannot be negative");
        }
        return new MoneyCent(result);
    }

    public MoneyCent multiply(int multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("Money cent multiplier cannot be negative");
        }
        return new MoneyCent(Math.multiplyExact(cent, multiplier));
    }
}
