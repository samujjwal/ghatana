package com.ghatana.catalog.ports;

import com.ghatana.platform.domain.domain.event.EventType;

import java.util.List;
import java.util.Optional;

/**
 * Port interface defining the contract for persisting and retrieving EventType instances.
 * 
 * <p>This is a hexagonal architecture port that abstracts the persistence layer for event types.
 * Adapters (memory, JDBC, etc.) must implement this interface to provide concrete storage
 * mechanisms. Supports CRUD operations, version management, and tenant-scoped queries.
 * 
 * @doc.type interface
 * @doc.purpose Defines the persistence contract for EventType entities in the catalog
 * @doc.layer product
 * @doc.pattern Repository
 * @since 2.0.0
 */
public interface EventTypeRepository {

    /**
     * Saves a new EventType. Implementations may reject duplicates for the same composite id.
     *
     * @param eventType domain model
     * @return saved instance
     */
    EventType save(EventType eventType);

    /**
     * Find by composite id "tenantId/namespace/name:version".
     */
    Optional<EventType> findById(String id);

    /**
     * Find by tenant, namespace, name, version string.
     */
    Optional<EventType> findByNameAndVersion(String tenantId, String namespace, String name, String version);

    /**
     * Find the latest version for a given (tenantId, namespace, name).
     */
    Optional<EventType> findLatest(String tenantId, String namespace, String name);

    /**
     * List all EventTypes. Implementations may support pagination later.
     */
    List<EventType> findAll();

    /**
     * @return total number of stored event types
     */
    long count();

    /**
     * Updates an existing EventType. The implementation should locate the event type
     * by its composite id and apply the changes.
     *
     * @param eventType updated domain model
     * @return updated instance
     * @throws IllegalArgumentException if the event type does not exist
     */
    EventType update(EventType eventType);

    /**
     * Deletes an EventType by its composite id.
     *
     * @param id composite id "tenantId/namespace/name:version"
     * @return true if deleted, false if not found
     */
    boolean delete(String id);
}
