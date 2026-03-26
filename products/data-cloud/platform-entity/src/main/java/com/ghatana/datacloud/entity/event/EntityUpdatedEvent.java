package com.ghatana.datacloud.entity.event;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event for entity updates.
 *
 * <p><b>Purpose</b><br>
 * Published when an entity's data is modified.
 *
 * <p><b>Change Detection</b><br>
 * Contains both old and new data to support:
 * <ul>
 *   <li>Diff computation for audit logs</li>
 *   <li>Undo/redo operations</li>
 *   <li>Change propagation to dependent systems</li>
 * </ul>
 *
 * @see EntityEvent
 * @doc.type class
 * @doc.purpose Domain event for entity update
 * @doc.layer domain
 * @doc.pattern Domain Event (DDD)
 */
public class EntityUpdatedEvent extends EntityEvent {

    private final Map<String, Object> oldData;
    private final Map<String, Object> newData;
    private final int newVersion;

    /**
     * Creates a new entity updated event.
     *
     * @param tenantId the tenant ID
     * @param collectionName the collection name
     * @param entityId the entity ID
     * @param actorId the user/system that updated the entity
     * @param oldData the entity data before update
     * @param newData the entity data after update
     * @param newVersion the new version number after update
     */
    public EntityUpdatedEvent(String tenantId, String collectionName, UUID entityId,
                              String actorId, Map<String, Object> oldData, 
                              Map<String, Object> newData, int newVersion) {
        super(tenantId, collectionName, entityId, actorId);
        this.oldData = oldData != null ? Collections.unmodifiableMap(oldData) : Collections.emptyMap();
        this.newData = newData != null ? Collections.unmodifiableMap(newData) : Collections.emptyMap();
        this.newVersion = newVersion;
    }

    /**
     * Returns the entity data before update.
     *
     * @return immutable copy of old data
     */
    public Map<String, Object> getOldData() {
        return oldData;
    }

    /**
     * Returns the entity data after update.
     *
     * @return immutable copy of new data
     */
    public Map<String, Object> getNewData() {
        return newData;
    }

    /**
     * Returns the new version number after update.
     *
     * @return version number
     */
    public int getNewVersion() {
        return newVersion;
    }

    @Override
    public String toString() {
        return "EntityUpdatedEvent{" +
                "eventId=" + getEventId() +
                ", tenantId='" + getTenantId() + '\'' +
                ", collectionName='" + getCollectionName() + '\'' +
                ", entityId=" + getEntityId() +
                ", actorId='" + getActorId() + '\'' +
                ", newVersion=" + newVersion +
                ", occurredAt=" + getOccurredAt() +
                '}';
    }
}
