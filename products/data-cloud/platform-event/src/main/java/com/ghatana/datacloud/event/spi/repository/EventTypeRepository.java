package com.ghatana.datacloud.event.spi.repository;

import com.ghatana.datacloud.event.model.EventType;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for EventType persistence operations.
 *
 * <p><b>Purpose</b><br>
 * Provides data access operations for EventType entities (schema definitions).
 * EventTypes define the structure and governance for events.
 *
 * <p><b>Lifecycle Management</b><br>
 * EventTypes follow a lifecycle:
 * <pre>
 * DRAFT → ACTIVE → DEPRECATED → RETIRED
 * </pre>
 *
 * <p><b>Versioning</b><br>
 * EventTypes are versioned (schemaVersion). Multiple versions can exist
 * simultaneously for backward compatibility.
 *
 * @see EventType
 * @doc.type interface
 * @doc.purpose Repository for EventType persistence
 * @doc.layer spi
 * @doc.pattern Repository
 */
public interface EventTypeRepository {

    // ==================== Read Operations ====================

    /**
     * Find event type by ID.
     *
     * @param tenantId tenant for isolation
     * @param eventTypeId event type UUID
     * @return Promise with event type if found
     */
    Promise<Optional<EventType>> findById(String tenantId, String eventTypeId);

    /**
     * Find event type by name (latest version).
     *
     * @param tenantId tenant for isolation
     * @param name event type name
     * @return Promise with latest version if found
     */
    Promise<Optional<EventType>> findByName(String tenantId, String name);

    /**
     * Find event type by name and version.
     *
     * @param tenantId tenant for isolation
     * @param name event type name
     * @param schemaVersion specific version
     * @return Promise with event type if found
     */
    Promise<Optional<EventType>> findByNameAndVersion(
        String tenantId,
        String name,
        String schemaVersion
    );

    /**
     * Find event types by namespace.
     *
     * @param tenantId tenant for isolation
     * @param namespace namespace prefix
     * @return Promise with list of event types in namespace
     */
    Promise<List<EventType>> findByNamespace(String tenantId, String namespace);

    /**
     * Find event types by tag.
     *
     * @param tenantId tenant for isolation
     * @param tag tag to search
     * @return Promise with list of tagged event types
     */
    Promise<List<EventType>> findByTag(String tenantId, String tag);

    /**
     * Find event types by tags (any match).
     *
     * @param tenantId tenant for isolation
     * @param tags tags to search
     * @return Promise with list of matched event types
     */
    Promise<List<EventType>> findByTagsAny(String tenantId, Set<String> tags);

    /**
     * Find event types by lifecycle status.
     *
     * @param tenantId tenant for isolation
     * @param status lifecycle status
     * @return Promise with list of event types
     */
    Promise<List<EventType>> findByStatus(String tenantId, EventType.LifecycleStatus status);

    /**
     * Find all event types for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with list of all event types
     */
    Promise<List<EventType>> findAll(String tenantId);

    /**
     * Find all versions of an event type.
     *
     * @param tenantId tenant for isolation
     * @param name event type name
     * @return Promise with list of all versions
     */
    Promise<List<EventType>> findAllVersions(String tenantId, String name);

    // ==================== Count Operations ====================

    /**
     * Count event types for tenant.
     *
     * @param tenantId tenant for isolation
     * @return Promise with count
     */
    Promise<Long> countByTenant(String tenantId);

    /**
     * Count event types by status.
     *
     * @param tenantId tenant for isolation
     * @param status lifecycle status
     * @return Promise with count
     */
    Promise<Long> countByStatus(String tenantId, EventType.LifecycleStatus status);

    // ==================== Persistence Operations ====================

    /**
     * Save event type.
     *
     * @param eventType event type to save
     * @return Promise with saved event type
     */
    Promise<EventType> save(EventType eventType);

    /**
     * Update event type.
     *
     * <p>Only certain fields can be updated based on lifecycle status.</p>
     *
     * @param eventType event type to update
     * @return Promise with updated event type
     */
    Promise<EventType> update(EventType eventType);

    /**
     * Update lifecycle status.
     *
     * @param tenantId tenant for isolation
     * @param eventTypeId event type UUID
     * @param newStatus new lifecycle status
     * @return Promise with updated event type
     */
    Promise<EventType> updateStatus(
        String tenantId,
        String eventTypeId,
        EventType.LifecycleStatus newStatus
    );

    /**
     * Check if event type exists by name.
     *
     * @param tenantId tenant for isolation
     * @param name event type name
     * @return Promise with existence flag
     */
    Promise<Boolean> existsByName(String tenantId, String name);

    /**
     * Check if event type exists by name and version.
     *
     * @param tenantId tenant for isolation
     * @param name event type name
     * @param schemaVersion specific version
     * @return Promise with existence flag
     */
    Promise<Boolean> existsByNameAndVersion(String tenantId, String name, String schemaVersion);

    // ==================== Delete Operations ====================

    /**
     * Delete event type by ID.
     *
     * <p>Only allowed if no events reference this type.</p>
     *
     * @param tenantId tenant for isolation
     * @param eventTypeId event type UUID
     * @return Promise with deletion success flag
     */
    Promise<Boolean> deleteById(String tenantId, String eventTypeId);

    /**
     * Soft delete (set active=false).
     *
     * @param tenantId tenant for isolation
     * @param eventTypeId event type UUID
     * @return Promise with updated event type
     */
    Promise<EventType> softDelete(String tenantId, String eventTypeId);
}
