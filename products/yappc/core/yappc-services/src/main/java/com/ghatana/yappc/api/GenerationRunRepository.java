/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Repository for durable generation run storage and retrieval.
 *
 * <p>This interface provides persistence for the canonical prompt→plan→confirm→generate→preview→download workflow,
 * ensuring that generation runs with provenance and review status are durable and queryable.
 *
 * @doc.type interface
 * @doc.purpose Repository for durable generation run storage and retrieval
 * @doc.layer api
 * @doc.pattern Repository
 */
public interface GenerationRunRepository {

    /**
     * Saves a generation run.
     *
     * @param run the generation run to save
     * @return a promise that completes when the run is saved
     */
    Promise<Void> save(@NotNull GenerationRun run);

    /**
     * Retrieves a generation run by ID.
     *
     * @param id the generation run ID
     * @return a promise that completes with the generation run, or null if not found
     */
    Promise<GenerationRun> findById(@NotNull String id);

    /**
     * Retrieves generation runs by project ID.
     *
     * @param projectId the project ID
     * @return a promise that completes with the list of generation runs for the project
     */
    Promise<java.util.List<GenerationRun>> findByProjectId(@NotNull String projectId);

    /**
     * Retrieves generation runs by plan ID.
     *
     * @param planId the plan ID
     * @return a promise that completes with the list of generation runs for the plan
     */
    Promise<java.util.List<GenerationRun>> findByPlanId(@NotNull String planId);

    /**
     * Updates the review status of a generation run.
     *
     * @param id the generation run ID
     * @param reviewStatus the new review status
     * @return a promise that completes when the status is updated
     */
    Promise<Void> updateReviewStatus(@NotNull String id, @NotNull GenerationRun.ReviewStatus reviewStatus);

    /**
     * Updates the status of a generation run.
     *
     * @param id the generation run ID
     * @param status the new status
     * @return a promise that completes when the status is updated
     */
    Promise<Void> updateStatus(@NotNull String id, @NotNull GenerationRun.RunStatus status);

    /**
     * Associates a preview session with a generation run.
     *
     * @param id the generation run ID
     * @param previewSessionId the preview session ID
     * @return a promise that completes when the association is saved
     */
    Promise<Void> associatePreviewSession(@NotNull String id, @NotNull String previewSessionId);
}
