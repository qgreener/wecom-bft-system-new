package com.wecombft.domain.model;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public record IdempotencyKey(String value) {

    public static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        value = normalize(value);
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("idempotency key must be no longer than 128 characters");
        }
    }

    public static IdempotencyKey of(String value) {
        return new IdempotencyKey(value);
    }

    public static IdempotencyKey ofParts(String firstPart, String... remainingParts) {
        String first = normalizePart(firstPart);
        String tail = Arrays.stream(Objects.requireNonNull(remainingParts, "parts must not be null"))
                .map(IdempotencyKey::normalizePart)
                .collect(Collectors.joining(":"));
        return tail.isBlank() ? new IdempotencyKey(first) : new IdempotencyKey(first + ":" + tail);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        return value.trim();
    }

    private static String normalizePart(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key part must not be blank");
        }
        return value.trim();
    }
}
