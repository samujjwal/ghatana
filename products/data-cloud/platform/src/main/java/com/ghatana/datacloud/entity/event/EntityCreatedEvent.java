package com.ghatana.datacloud.entity.event;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event for entity creation.
 *
 * <p><b>Purpose</b><br>
 * Published when a new entity is created in any collection.
 *
 * <p><b>Subscribers</b><br>
 * <ul>
 *   <li>Search index updater - adds entity to search index</li>
 *   <li>Audit logger - records creation event</li>
 *   <li>Webhook dispatcher - notifies external systems</li>
 *   <li>Analytics - updates collection metrics</li>
 * </ul>
 *
 * @see EntityEvent
 * @doc.type class
 * @doc.purpose Domain event for entity creation
 * @doc.layer domain
 * @doc.pattern Domain Event (DDD)
 */
public class EntityCreatedEvent extends EntityEvent {

    private final Map<String, Object> data;

    /**
     * Creates a new entity created event.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entityId the new entity ID
     * @param actorId the user/system that created the entity
     * @param data the entity data at creation time
     */
    public EntityCreatedEvent(String tenantId, String collectionName, UUID entityId, 
                              String actorId, Map<String, Object> data) {
        super(tenantId, collectionName, entityId, actorId);
        this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
    }

    /**
     * Returns the entity data at creation time.
     *
     * @return immutable copy of entity data
     */
    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "EntityCreatedEvent{" +
                "eventId=" + getEventId() +
                ", tenantId='" + getTenantId() + '\'' +
                ", collectionName='" + getCollectionName() + '\'' +
                ", entityId=" + getEntityId() +
                ", actorId='" + getActorId() + '\'' +
                ", occurredAt=" + getOccurredAt() +
                '}';
    }
}
