package com.ghatana.datacloud.entity.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events in Data-Cloud.
 *
 * <p><b>Purpose</b><br>
 * Domain events capture significant occurrences in the domain that domain experts
 * care about. They enable loose coupling between aggregates and support event-driven
 * architectures.
 *
 * <p><b>Event Sourcing</b><br>
 * Domain events can be used for:
 * <ul>
 *   <li>Audit trail - recording what happened and when</li>
 *   <li>Integration - notifying external systems</li>
 *   <li>Event sourcing - rebuilding state from events</li>
 *   <li>CQRS - updating read models asynchronously</li>
 * </ul>
 *
 * <p><b>Naming Convention</b><br>
 * Events should be named in past tense (e.g., EntityCreated, CollectionUpdated).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * public class EntityCreatedEvent implements DomainEvent {
 *     private final UUID eventId = UUID.randomUUID();
 *     private final Instant occurredAt = Instant.now();
 *     private final String tenantId;
 *     private final String collectionName;
 *     private final UUID entityId;
 *
 *     // constructor, getters...
 * }
 * }</pre>
 *
 * @see DomainEventPublisher
 * @see EntityEvent
 * @doc.type interface
 * @doc.purpose Base interface for domain events
 * @doc.layer domain
 * @doc.pattern Domain Event (DDD)
 */
public interface DomainEvent {

    /**
     * Returns the unique identifier for this event.
     *
     * @return event ID (never null)
     */
    UUID getEventId();

    /**
     * Returns the tenant this event belongs to.
     *
     * @return tenant ID (never null)
     */
    String getTenantId();

    /**
     * Returns when this event occurred.
     *
     * @return timestamp of occurrence (never null)
     */
    Instant getOccurredAt();

    /**
     * Returns the type name of this event.
     * <p>
     * Used for routing and serialization.
     *
     * @return event type name (e.g., "EntityCreated", "CollectionUpdated")
     */
    default String getEventType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Returns the aggregate ID this event relates to.
     *
     * @return aggregate ID (may be null for cross-aggregate events)
     */
    UUID getAggregateId();

    /**
     * Returns the aggregate type this event relates to.
     *
     * @return aggregate type (e.g., "Entity", "Collection")
     */
    String getAggregateType();
}
