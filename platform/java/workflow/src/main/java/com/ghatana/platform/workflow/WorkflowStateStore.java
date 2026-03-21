/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting workflow run state.
 *
 * <p>Implementations provide storage for {@link WorkflowRun} instances. The platform
 * ships two implementations:
 * <ul>
 *   <li>{@code InMemoryWorkflowStateStore} — for ephemeral workflows and testing</li>
 *   <li>{@code JdbcWorkflowStateStore} (in {@code workflow-jdbc}) — PostgreSQL persistence</li>
 * </ul>
 *
 * <p>All methods return {@link Promise} for non-blocking operation on ActiveJ's eventloop.
 *
 * @doc.type interface
 * @doc.purpose SPI for workflow run state persistence
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface WorkflowStateStore {

    /**
     * Persists or updates a workflow run.
     *
     * @param run the workflow run to save
     * @return a promise that completes when the save is durable
     */
    Promise<Void> save(@NotNull WorkflowRun run);

    /**
     * Finds a workflow run by its unique run ID.
     *
     * @param runId the run identifier
     * @return a promise resolving to the run, or empty if not found
     */
    Promise<Optional<WorkflowRun>> findByRunId(@NotNull String runId);

    /**
     * Finds all runs for a given workflow definition.
     *
     * @param workflowId the workflow definition identifier
     * @return a promise resolving to the list of runs (may be empty)
     */
    Promise<List<WorkflowRun>> findByWorkflowId(@NotNull String workflowId);

    /**
     * Finds all runs currently in the given status.
     *
     * @param status the status to filter by
     * @return a promise resolving to matching runs
     */
    Promise<List<WorkflowRun>> findByStatus(@NotNull WorkflowRunStatus status);

    /**
     * Finds WAITING runs whose wait condition has expired (fire_at &lt; cutoff).
     *
     * @param cutoff the cutoff instant — waits with fire_at before this are expired
     * @return a promise resolving to expired waiting runs
     */
    Promise<List<WorkflowRun>> findExpiredWaits(@NotNull Instant cutoff);

    /**
     * Updates the status of a run (lightweight alternative to full save).
     *
     * @param runId  the run identifier
     * @param status the new status
     * @return a promise that completes when the update is durable
     */
    Promise<Void> updateStatus(@NotNull String runId, @NotNull WorkflowRunStatus status);

    /**
     * Deletes a workflow run and its associated state.
     *
     * @param runId the run identifier to delete
     */
    void delete(@NotNull String runId);

    /**
     * Stores workflow metrics for a run.
     *
     * @param metrics the workflow metrics to store
     * @return a promise that completes when the metrics are stored
     */
    Promise<Void> storeMetrics(@NotNull Object metrics);
}
