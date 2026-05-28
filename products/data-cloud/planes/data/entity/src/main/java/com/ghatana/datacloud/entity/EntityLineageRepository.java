package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for entity lineage data access.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for entity lineage persistence operations.
 * Used to track provenance, change history, and parent-child relationships.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityLineageRepository repo = ...;
 *
 * // Save lineage record
 * EntityLineage lineage = EntityLineage.builder()
 *     .entityId(entityId)
 *     .sourceType("ingest")
 *     .sourceId("csv-import-456")
 *     .build();
 * repo.save(lineage);
 *
 * // Get lineage for an entity
 * Promise<List<EntityLineage>> lineage = repo.findByEntityId(entityId);
 *
 * // Get children of a parent entity
 * Promise<List<EntityLineage>> children = repo.findByParentEntityId(parentId);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 *
 * @see EntityLineage
 * @doc.type interface
 * @doc.purpose Repository port for entity lineage persistence
 * @doc.layer product
 * @doc.pattern Repository Port (Domain Layer)
 */
public interface EntityLineageRepository {

    /**
     * Finds all lineage records for an entity.
     *
     * @param entityId the entity ID (required)
     * @return Promise of list of lineage records
     */
    Promise<List<EntityLineage>> findByEntityId(UUID entityId);

    /**
     * Finds lineage records by tenant and collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @return Promise of list of lineage records
     */
    Promise<List<EntityLineage>> findByTenantAndCollection(String tenantId, String collectionName);

    /**
     * Finds lineage records by source.
     *
     * @param sourceType the source type (required)
     * @param sourceId the source ID (required)
     * @return Promise of list of lineage records
     */
    Promise<List<EntityLineage>> findBySource(String sourceType, String sourceId);

    /**
     * Finds lineage records by parent entity ID.
     *
     * @param parentEntityId the parent entity ID (required)
     * @return Promise of list of child lineage records
     */
    Promise<List<EntityLineage>> findByParentEntityId(UUID parentEntityId);

    /**
     * Saves a lineage record.
     *
     * @param lineage the lineage record to save (required)
     * @return Promise of saved lineage record
     */
    Promise<EntityLineage> save(EntityLineage lineage);

    /**
     * Deletes lineage records for an entity.
     *
     * @param entityId the entity ID (required)
     * @return Promise of void
     */
    Promise<Void> deleteByEntityId(UUID entityId);

    /**
     * Finds the full lineage chain (ancestors) for an entity.
     *
     * <p><b>Chain Traversal</b><br>
     * Recursively follows parent relationships to build the full ancestry chain.
     *
     * @param entityId the entity ID (required)
     * @param maxDepth maximum depth to traverse (default: 10)
     * @return Promise of list of lineage records in ancestor order (oldest first)
     */
    Promise<List<EntityLineage>> findAncestryChain(UUID entityId, int maxDepth);
}
