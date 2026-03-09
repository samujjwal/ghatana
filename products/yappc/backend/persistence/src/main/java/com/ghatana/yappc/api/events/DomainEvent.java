/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all domain events in YAPPC.
 *
 * <p>Events are immutable records of something that happened in the domain.
 * They carry a monotonically increasing {@code sequenceNum} scoped to their aggregate,
 * enabling ordered replay and optimistic concurrency checks.
 *
 * @doc.type class
 * @doc.purpose Base domain event
 * @doc.layer domain
 * @doc.pattern Event Sourcing
 */
public abstract class DomainEvent {

    private final String eventId;
    private final String eventType;
    private final String aggregateId;
    private final String aggregateType;
    private final String tenantId;
    private final String userId;
    private final Instant occurredAt;
    private final int schemaVersion;

    protected DomainEvent(
            String eventType,
            String aggregateId,
            String aggregateType,
            String tenantId,
            String userId) {
        this.eventId       = UUID.randomUUID().toString();
        this.eventType     = eventType;
        this.aggregateId   = aggregateId;
        this.aggregateType = aggregateType;
        this.tenantId      = tenantId;
        this.userId        = userId;
        this.occurredAt    = Instant.now();
        this.schemaVersion = 1;
    }

    public String getEventId()       { return eventId; }
    public String getEventType()     { return eventType; }
    public String getAggregateId()   { return aggregateId; }
    public String getAggregateType() { return aggregateType; }
    public String getTenantId()      { return tenantId; }
    public String getUserId()        { return userId; }
    public Instant getOccurredAt()   { return occurredAt; }
    public int getSchemaVersion()    { return schemaVersion; }

    /**
     * Returns the event payload as a flat Map for serialisation.
     * Subclasses must implement this to expose their specific fields.
     */
    public abstract Map<String, Object> toPayload();

    @Override
    public String toString() {
        return getClass().getSimpleName() +
               "{eventId='" + eventId + '\'' +
               ", aggregateId='" + aggregateId + '\'' +
               ", tenantId='" + tenantId + '\'' +
               ", occurredAt=" + occurredAt + '}';
    }
}
