package com.ghatana.yappc.ai.requirements.application.workspace;

import com.ghatana.yappc.ai.requirements.domain.workspace.Workspace;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Workspace repository interface.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for persisting and retrieving workspace data.
 * Implementations will use core/database abstractions for PostgreSQL.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Workspace ws = new Workspace(...);
 * repository.save(ws)
 *     .map(savedWs -> {
 *         logger.info("Workspace saved: {}", savedWs.workspaceId());
 *         return savedWs;
 *     })
 *     .get();
 * }</pre>
 *
 * <p><b>Integration</b><br>
 * - Uses ActiveJ Promise for async operations
 * - Implemented by PostgreSQLWorkspaceRepository with Hibernate
 * - Extends core/database abstractions
 *
 * @doc.type interface
 * @doc.purpose Workspace data access abstraction
 * @doc.layer product
 * @doc.pattern Repository
 * @see Workspace
 * @see WorkspaceService
 */
public interface WorkspaceRepository {
    /**
     * Save workspace to database.
     *
     * @param workspace Workspace to save
     * @return Promise of saved workspace
     */
    Promise<Workspace> save(Workspace workspace);

    /**
     * Find workspace by ID.
     *
     * @param workspaceId Workspace ID
     * @return Promise of Optional workspace
     */
    Promise<Optional<Workspace>> findById(String workspaceId);

    /**
     * Find all workspaces owned by a user.
     *
     * @param ownerId Owner user ID
     * @return Promise of workspace list
     */
    Promise<List<Workspace>> findByOwnerId(String ownerId);

    /**
     * Find all workspaces where user is a member.
     *
     * @param userId User ID
     * @return Promise of workspace list
     */
    Promise<List<Workspace>> findByMember(String userId);

    /**
     * Find all workspaces for org unit.
     *
     * @param orgUnitId Org unit ID
     * @return Promise of workspace list
     */
    Promise<List<Workspace>> findByOrgUnit(String orgUnitId);

    /**
     * Update existing workspace.
     *
     * @param workspace Workspace to update
     * @return Promise of updated workspace
     */
    Promise<Workspace> update(Workspace workspace);

    /**
     * Delete workspace.
     *
     * @param workspaceId Workspace ID to delete
     * @return Promise completion
     */
    Promise<Void> delete(String workspaceId);

    /**
     * Check if workspace exists.
     *
     * @param workspaceId Workspace ID
     * @return Promise of existence check
     */
    Promise<Boolean> exists(String workspaceId);
}