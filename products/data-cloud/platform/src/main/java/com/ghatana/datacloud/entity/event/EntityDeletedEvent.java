package com.ghatana.datacloud.entity.event;

import java.util.UUID;

/**
 * Domain event for entity deletion.
 *
 * <p><b>Purpose</b><br>
 * Published when an entity is soft-deleted (active=false).
 *
 * <p><b>Subscribers</b><br>
 * <ul>
 *   <li>Search index updater - removes entity from search index</li>
 *   <li>Audit logger - records deletion event</li>
 *   <li>Webhook dispatcher - notifies external systems</li>
 *   <li>Cascade handler - handles dependent entity cleanup</li>
 * </ul>
 *
 * @see EntityEvent
 * @doc.type class
 * @doc.purpose Domain event for entity deletion
 * @doc.layer domain
 * @doc.pattern Domain Event (DDD)
 */
public class EntityDeletedEvent extends EntityEvent {

    private final boolean hardDelete;

    /**
     * Creates a new entity deleted event.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entityId the deleted entity ID
     * @param actorId the user/system that deleted the entity
     * @param hardDelete true if permanently deleted, false if soft-deleted
     */
    public EntityDeletedEvent(String tenantId, String collectionName, UUID entityId,
                              String actorId, boolean hardDelete) {
        super(tenantId, collectionName, entityId, actorId);
        this.hardDelete = hardDelete;
    }

    /**
     * Returns whether this was a hard delete (permanent) or soft delete.
     *
     * @return true for permanent deletion, false for soft delete
     */
    public boolean isHardDelete() {
        return hardDelete;
    }

    @Override
    public String toString() {
        return "EntityDeletedEvent{" +
                "eventId=" + getEventId() +
                ", tenantId='" + getTenantId() + '\'' +
                ", collectionName='" + getCollectionName() + '\'' +
                ", entityId=" + getEntityId() +
                ", actorId='" + getActorId() + '\'' +
                ", hardDelete=" + hardDelete +
                ", occurredAt=" + getOccurredAt() +
                '}';
    }
}
