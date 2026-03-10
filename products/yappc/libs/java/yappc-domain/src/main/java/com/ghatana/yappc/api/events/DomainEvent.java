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
 * <p>Distributed tracing fields:
 * <ul>
 *   <li>{@link #correlationId} — ID shared across all events in a single user request span
 *   (propagated from the inbound HTTP header {@code X-Correlation-ID}).</li>
 *   <li>{@link #causationId} — ID of the event that <em>caused</em> this event, enabling
 *   cause-and-effect lineage across event chains.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Base domain event with schema versioning and distributed traceability
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

    /**
     * Correlation ID linking all events emitted within the same user request or
     * business transaction. Propagated from {@code X-Correlation-ID} header.
     */
    private final String correlationId;

    /**
     * ID of the event that caused this event (causal lineage).
     * May be {@code null} for root events that were not triggered by another event.
     */
    private final String causationId;

    /**
     * Constructs a domain event with schema version 1 and a generated event ID.
     * Correlation and causation IDs default to {@code null}.
     */
    protected DomainEvent(
            String eventType,
            String aggregateId,
            String aggregateType,
            String tenantId,
            String userId) {
        this(eventType, aggregateId, aggregateType, tenantId, userId, null, null);
    }

    /**
     * Constructs a domain event with explicit correlation and causation IDs for
     * distributed trace propagation.
     *
     * @param eventType     event type identifier (e.g., {@code "PhaseAdvanced"})
     * @param aggregateId   aggregate's unique identifier
     * @param aggregateType aggregate type name (e.g., {@code "Project"})
     * @param tenantId      tenant owning this event
     * @param userId        user or service that triggered the event
     * @param correlationId request-level correlation ID (nullable)
     * @param causationId   ID of the causing event (nullable for root events)
     */
    protected DomainEvent(
            String eventType,
            String aggregateId,
            String aggregateType,
            String tenantId,
            String userId,
            String correlationId,
            String causationId) {
        this.eventId       = UUID.randomUUID().toString();
        this.eventType     = eventType;
        this.aggregateId   = aggregateId;
        this.aggregateType = aggregateType;
        this.tenantId      = tenantId;
        this.userId        = userId;
        this.occurredAt    = Instant.now();
        this.schemaVersion = currentSchemaVersion();
        this.correlationId = correlationId;
        this.causationId   = causationId;
    }

    /**
     * Returns the schema version for this event type.
     * Subclasses may override to return a version other than 1.
     * The schema version must be incremented whenever the event's
     * {@link #toPayload()} structure changes in a breaking way.
     *
     * @return schema version ≥ 1
     */
    protected int currentSchemaVersion() {
        return 1;
    }

    public String getEventId()        { return eventId; }
    public String getEventType()      { return eventType; }
    public String getAggregateId()    { return aggregateId; }
    public String getAggregateType()  { return aggregateType; }
    public String getTenantId()       { return tenantId; }
    public String getUserId()         { return userId; }
    public Instant getOccurredAt()    { return occurredAt; }
    public int getSchemaVersion()     { return schemaVersion; }
    public String getCorrelationId()  { return correlationId; }
    public String getCausationId()    { return causationId; }

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
               ", schemaVersion=" + schemaVersion +
               ", occurredAt=" + occurredAt + '}';
    }
}
