/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Workspace;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Workspace persistence.
 *
 * <p><b>Purpose</b><br>
 * Defines async CRUD operations for workspace management with multi-tenant isolation.
 *
 * <p><b>Implementations</b><br>
 * - InMemoryWorkspaceRepository: Development/testing - JdbcWorkspaceRepository: Production with
 * PostgreSQL
 *
 * @doc.type interface
 * @doc.purpose Workspace persistence port
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public interface WorkspaceRepository {

  /**
   * Save a workspace (create or update).
   *
   * @param workspace the workspace to save
   * @return Promise of saved workspace
   */
  Promise<Workspace> save(Workspace workspace);

  /**
   * Find workspace by ID.
   *
   * @param tenantId the tenant ID
   * @param id the workspace ID
   * @return Promise of Optional workspace
   */
  Promise<Optional<Workspace>> findById(String tenantId, UUID id);

  /**
   * Find all workspaces for a tenant.
   *
   * @param tenantId the tenant ID
   * @return Promise of list of workspaces
   */
  Promise<List<Workspace>> findByTenantId(String tenantId);

  /**
   * Find all workspaces where user is a member.
   *
   * @param tenantId the tenant ID
   * @param userId the user ID
   * @return Promise of list of workspaces
   */
  Promise<List<Workspace>> findByMemberUserId(String tenantId, String userId);

  /**
   * Find workspaces owned by a user.
   *
   * @param tenantId the tenant ID
   * @param ownerId the owner user ID
   * @return Promise of list of workspaces
   */
  Promise<List<Workspace>> findByOwnerId(String tenantId, String ownerId);

  /**
   * Delete a workspace.
   *
   * @param tenantId the tenant ID
   * @param id the workspace ID
   * @return Promise completing when deleted
   */
  Promise<Void> delete(String tenantId, UUID id);

  /**
   * Check if workspace exists.
   *
   * @param tenantId the tenant ID
   * @param id the workspace ID
   * @return Promise of boolean
   */
  Promise<Boolean> exists(String tenantId, UUID id);

  /**
   * Count workspaces for a tenant.
   *
   * @param tenantId the tenant ID
   * @return Promise of count
   */
  Promise<Long> count(String tenantId);
}
