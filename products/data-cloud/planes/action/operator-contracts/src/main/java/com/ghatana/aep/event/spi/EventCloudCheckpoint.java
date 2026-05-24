package com.ghatana.aep.event.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Stores consumer progress for EventCloud replay and recovery
 * @doc.layer product
 * @doc.pattern Contract
 */
public record EventCloudCheckpoint(
        String tenantId,
        String consumerId,
        EventCloudOffset offset,
        Instant checkpointedAt,
        Map<String, Object> metadata) {

    public EventCloudCheckpoint {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (consumerId == null || consumerId.isBlank()) {
            throw new IllegalArgumentException("consumerId must not be blank");
        }
        offset = Objects.requireNonNull(offset, "offset must not be null");
        checkpointedAt = Objects.requireNonNull(checkpointedAt, "checkpointedAt must not be null");
        metadata = Map.copyOf(metadata != null ? metadata : Map.of());
    }
}
