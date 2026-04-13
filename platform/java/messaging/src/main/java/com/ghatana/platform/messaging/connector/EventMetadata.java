package com.ghatana.core.connectors;

import com.ghatana.platform.types.identity.CorrelationId;
import com.ghatana.platform.types.identity.IdempotencyKey;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical metadata envelope shared across product event models.
 *
 * @doc.type record
 * @doc.purpose Shared event metadata contract for headers, correlation, idempotency, and partition routing
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record EventMetadata(
        Map<String, String> headers,
        Optional<CorrelationId> correlationId,
        Optional<IdempotencyKey> idempotencyKey,
        Optional<String> partitionKey
) {
    public EventMetadata {
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers required"));
        Objects.requireNonNull(correlationId, "correlationId required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey required");
        Objects.requireNonNull(partitionKey, "partitionKey required");
    }

    public static EventMetadata empty() {
        return new EventMetadata(Map.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
