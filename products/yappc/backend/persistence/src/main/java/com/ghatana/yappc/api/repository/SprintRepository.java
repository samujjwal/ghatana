/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Sprint;
import com.ghatana.yappc.api.domain.Sprint.SprintStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Sprint entities.
 *
 * @doc.type interface
 * @doc.purpose Repository for sprint persistence
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface SprintRepository {

    /**
     * Save a sprint.
     *
     * @param sprint the sprint to save
     * @return Promise with saved sprint
     */
    Promise<Sprint> save(Sprint sprint);

    /**
     * Find a sprint by ID.
     *
     * @param tenantId the tenant ID
     * @param id       the sprint ID
     * @return Promise with optional sprint
     */
    Promise<Optional<Sprint>> findById(String tenantId, UUID id);

    /**
     * Find all sprints for a project.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @return Promise with list of sprints
     */
    Promise<List<Sprint>> findByProject(String tenantId, String projectId);

    /**
     * Find the current active sprint for a project.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @return Promise with optional active sprint
     */
    Promise<Optional<Sprint>> findCurrentSprint(String tenantId, String projectId);

    /**
     * Find sprints by status.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @param status    the sprint status
     * @return Promise with list of sprints
     */
    Promise<List<Sprint>> findByStatus(String tenantId, String projectId, SprintStatus status);

    /**
     * Find completed sprints for velocity calculation.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @param limit     number of sprints to retrieve
     * @return Promise with list of completed sprints
     */
    Promise<List<Sprint>> findCompletedSprints(String tenantId, String projectId, int limit);

    /**
     * Delete a sprint.
     *
     * @param tenantId the tenant ID
     * @param id       the sprint ID
     * @return Promise with success status
     */
    Promise<Boolean> delete(String tenantId, UUID id);

    /**
     * Check if a sprint exists.
     *
     * @param tenantId the tenant ID
     * @param id       the sprint ID
     * @return Promise with existence status
     */
    Promise<Boolean> exists(String tenantId, UUID id);

    /**
     * Calculate average velocity for a project.
     *
     * @param tenantId  the tenant ID
     * @param projectId the project ID
     * @param lastN     number of sprints to consider
     * @return Promise with average velocity
     */
    Promise<Double> calculateAverageVelocity(String tenantId, String projectId, int lastN);
}
