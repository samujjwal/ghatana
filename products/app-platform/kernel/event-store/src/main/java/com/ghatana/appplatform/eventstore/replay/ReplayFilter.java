package com.ghatana.appplatform.eventstore.replay;

import java.time.Instant;
import java.util.Optional;

/**
 * Criteria for filtering events during replay.
 *
 * <p>All fields are optional. Null means "no filter on this dimension".
 *
 * @doc.type record
 * @doc.purpose Replay event filter (STORY-K05-021)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record ReplayFilter(
    String tenantId,           // required — replay is always tenant-scoped
    String aggregateType,      // null = all aggregate types
    String aggregateId,        // null = all aggregates
    String eventType,          // null = all event types
    Instant fromTimestamp,     // null = from beginning
    Instant toTimestamp,       // null = up to now
    Long fromSequence,         // null = from sequence 0
    Long toSequence            // null = no upper sequence bound
) {
    public ReplayFilter {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required for replay");
        }
    }

    public static ReplayFilter forTenant(String tenantId) {
        return new ReplayFilter(tenantId, null, null, null, null, null, null, null);
    }

    public static ReplayFilter forAggregate(String tenantId, String aggregateType, String aggregateId) {
        return new ReplayFilter(tenantId, aggregateType, aggregateId, null, null, null, null, null);
    }

    public ReplayFilter withTimeRange(Instant from, Instant to) {
        return new ReplayFilter(tenantId, aggregateType, aggregateId, eventType, from, to, fromSequence, toSequence);
    }

    public ReplayFilter withEventType(String type) {
        return new ReplayFilter(tenantId, aggregateType, aggregateId, type, fromTimestamp, toTimestamp, fromSequence, toSequence);
    }
}
