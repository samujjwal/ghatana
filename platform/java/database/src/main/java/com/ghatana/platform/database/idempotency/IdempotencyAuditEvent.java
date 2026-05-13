package com.ghatana.platform.database.idempotency;

import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Captures auditable idempotency decisions for mutating operations
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record IdempotencyAuditEvent(
        IdempotencyDecision decision,
        String operation,
        String key,
        String fingerprint,
        Instant occurredAt,
        boolean replayed,
        boolean expired,
        boolean conflict) {

    public IdempotencyAuditEvent {
        Objects.requireNonNull(decision, "decision must not be null");
        requireNonBlank(operation, "operation");
        requireNonBlank(key, "key");
        requireNonBlank(fingerprint, "fingerprint");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
