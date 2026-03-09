package com.ghatana.products.yappc.domain.repository;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Base repository interface for tenant-aware entities in the YAPPC platform.
 *
 * <p>This domain-level contract provides multi-tenant data isolation by requiring
 * all queries to be scoped to a workspace. All methods return ActiveJ Promises
 * for non-blocking async execution on the event loop.</p>
 *
 * <p>All entities implementing this interface must have a {@code workspaceId} field
 * that is used for tenant isolation.</p>
 *
 * @param <T>  the entity type
 * @param <ID> the entity's primary key type
 *
 * @doc.type interface
 * @doc.purpose Provides base repository contract for multi-tenant data access
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface TenantAwareRepository<T, ID> {

    /**
     * Finds an entity by ID within a specific workspace.
     *
     * @param id          the entity ID
     * @param workspaceId the workspace ID for tenant isolation
     * @return promise of an Optional containing the entity if found
     */
    Promise<Optional<T>> findByIdAndWorkspaceId(ID id, UUID workspaceId);

    /**
     * Finds all entities belonging to a workspace.
     *
     * @param workspaceId the workspace ID for tenant isolation
     * @return promise of entities in the workspace
     */
    Promise<List<T>> findByWorkspaceId(UUID workspaceId);

    /**
     * Deletes an entity by ID within a specific workspace.
     *
     * @param id          the entity ID
     * @param workspaceId the workspace ID for tenant isolation
     * @return promise completing when deleted
     */
    Promise<Void> deleteByIdAndWorkspaceId(ID id, UUID workspaceId);

    /**
     * Checks if an entity exists by ID within a specific workspace.
     *
     * @param id          the entity ID
     * @param workspaceId the workspace ID for tenant isolation
     * @return promise of true if the entity exists
     */
    Promise<Boolean> existsByIdAndWorkspaceId(ID id, UUID workspaceId);

    /**
     * Counts all entities belonging to a workspace.
     *
     * @param workspaceId the workspace ID for tenant isolation
     * @return promise of the count of entities
     */
    Promise<Long> countByWorkspaceId(UUID workspaceId);
}
