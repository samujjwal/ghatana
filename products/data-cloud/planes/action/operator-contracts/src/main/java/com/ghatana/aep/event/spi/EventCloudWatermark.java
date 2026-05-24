package com.ghatana.aep.event.spi;

import java.time.Instant;
import java.util.Objects;

/**
 * @doc.type record
 * @doc.purpose Captures EventCloud event-time progress for a tenant partition
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record EventCloudWatermark(String tenantId, String partition, Instant eventTime, EventCloudOffset offset) {

    public EventCloudWatermark {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (partition == null || partition.isBlank()) {
            throw new IllegalArgumentException("partition must not be blank");
        }
        eventTime = Objects.requireNonNull(eventTime, "eventTime must not be null");
        offset = Objects.requireNonNull(offset, "offset must not be null");
    }
}
