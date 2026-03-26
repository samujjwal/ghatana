package com.ghatana.datacloud.entity.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for entity-related domain events.
 *
 * <p><b>Purpose</b><br>
 * Provides common fields for all entity lifecycle events (created, updated, deleted).
 *
 * <p><b>Concrete Events</b><br>
 * <ul>
 *   <li>{@link EntityCreatedEvent} - new entity created</li>
 *   <li>{@link EntityUpdatedEvent} - entity data modified</li>
 *   <li>{@link EntityDeletedEvent} - entity soft-deleted</li>
 * </ul>
 *
 * @see DomainEvent
 * @doc.type class
 * @doc.purpose Base class for entity events
 * @doc.layer domain
 * @doc.pattern Domain Event (DDD)
 */
public abstract class EntityEvent implements DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;
    private final String tenantId;
    private final String collectionName;
    private final UUID entityId;
    private final String actorId;

    /**
     * Creates a new entity event.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entityId the entity ID
     * @param actorId the user/system that triggered this event
     */
    protected EntityEvent(String tenantId, String collectionName, UUID entityId, String actorId) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.tenantId = tenantId;
        this.collectionName = collectionName;
        this.entityId = entityId;
        this.actorId = actorId;
    }

    @Override
    public UUID getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public UUID getAggregateId() {
        return entityId;
    }

    @Override
    public String getAggregateType() {
        return "Entity";
    }

    public String getCollectionName() {
        return collectionName;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getActorId() {
        return actorId;
    }
}
