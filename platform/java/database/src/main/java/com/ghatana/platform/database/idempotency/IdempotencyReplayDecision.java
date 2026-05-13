package com.ghatana.platform.database.idempotency;

import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Represents whether a mutation should execute or replay an existing result
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record IdempotencyReplayDecision<T>(
        IdempotencyDecision decision,
        Optional<T> result,
        IdempotencyAuditEvent auditEvent) {

    public IdempotencyReplayDecision {
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(auditEvent, "auditEvent must not be null");
        if (decision == IdempotencyDecision.COMPLETED && result.isEmpty()) {
            throw new IllegalArgumentException("completed replay decisions must include a result");
        }
        if (decision != IdempotencyDecision.COMPLETED && result.isPresent()) {
            throw new IllegalArgumentException("non-completed decisions must not include a result");
        }
    }

    public boolean shouldReplay() {
        return decision == IdempotencyDecision.COMPLETED;
    }
}
