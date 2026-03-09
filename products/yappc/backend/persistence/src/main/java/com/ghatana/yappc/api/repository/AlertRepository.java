/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for SecurityAlert persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Alert repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface AlertRepository {

    /** Save an alert. */
    Promise<SecurityAlert> save(SecurityAlert alert);

    /** Find an alert by ID. */
    Promise<SecurityAlert> findById(UUID workspaceId, UUID id);

    /** Find alerts by project. */
    Promise<List<SecurityAlert>> findByProject(UUID workspaceId, UUID projectId);

    /** Find alerts by status. */
    Promise<List<SecurityAlert>> findByStatus(UUID workspaceId, String status);

    /** Find alerts by severity. */
    Promise<List<SecurityAlert>> findBySeverity(UUID workspaceId, String severity);

    /** Find open alerts. */
    Promise<List<SecurityAlert>> findOpen(UUID workspaceId);

    /** Find open alerts by project. */
    Promise<List<SecurityAlert>> findOpenByProject(UUID workspaceId, UUID projectId);

    /** Find alerts assigned to a user. */
    Promise<List<SecurityAlert>> findByAssignedTo(UUID workspaceId, UUID userId);

    /** Count open alerts by severity. */
    Promise<Long> countOpenBySeverity(UUID workspaceId, String severity);

    /** Delete an alert. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if an alert exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
