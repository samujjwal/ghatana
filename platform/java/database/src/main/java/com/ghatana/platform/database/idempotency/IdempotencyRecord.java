package com.ghatana.platform.database.idempotency;

import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Stores a completed mutation result and replay metadata for an idempotency key
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record IdempotencyRecord<T>(
        String operation,
        String key,
        String fingerprint,
        T result,
        Instant createdAt,
        Instant expiresAt) {

    public IdempotencyRecord {
        requireNonBlank(operation, "operation");
        requireNonBlank(key, "key");
        requireNonBlank(fingerprint, "fingerprint");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt must be after createdAt");
        }
    }

    public boolean isExpired(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
