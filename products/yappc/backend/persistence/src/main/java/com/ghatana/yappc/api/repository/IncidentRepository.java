/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.Incident;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Incident persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Incident repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface IncidentRepository {

    /** Save an incident. */
    Promise<Incident> save(Incident incident);

    /** Find an incident by ID within a workspace. */
    Promise<Incident> findById(UUID workspaceId, UUID id);

    /** Find incidents by project. */
    Promise<List<Incident>> findByProject(UUID workspaceId, UUID projectId);

    /** Find incidents by status. */
    Promise<List<Incident>> findByStatus(UUID workspaceId, String status);

    /** Find incidents by severity. */
    Promise<List<Incident>> findBySeverity(UUID workspaceId, String severity);

    /** Find open incidents. */
    Promise<List<Incident>> findOpen(UUID workspaceId);

    /** Find open incidents by project. */
    Promise<List<Incident>> findOpenByProject(UUID workspaceId, UUID projectId);

    /** Find incidents within a time range. */
    Promise<List<Incident>> findByTimeRange(UUID workspaceId, Instant start, Instant end);

    /** Count open incidents. */
    Promise<Long> countOpen(UUID workspaceId);

    /** Count incidents by severity. */
    Promise<Long> countBySeverity(UUID workspaceId, String severity);

    /** Delete an incident. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if an incident exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
