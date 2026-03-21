/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link WorkflowStateStore} for ephemeral workflows and testing.
 *
 * <p>State is held in a {@link ConcurrentHashMap} and is lost on JVM shutdown. For durable
 * persistence, use {@code JdbcWorkflowStateStore} from the {@code workflow-jdbc} module.
 *
 * @doc.type class
 * @doc.purpose In-memory workflow state store for testing and ephemeral workflows
 * @doc.layer core
 * @doc.pattern Repository
 */
public final class InMemoryWorkflowStateStore implements WorkflowStateStore {

    private final ConcurrentHashMap<String, WorkflowRun> runs = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> save(@NotNull WorkflowRun run) {
        Objects.requireNonNull(run, "run");
        runs.put(run.runId(), run);
        return Promise.complete();
    }

    @Override
    public Promise<Optional<WorkflowRun>> findByRunId(@NotNull String runId) {
        Objects.requireNonNull(runId, "runId");
        return Promise.of(Optional.ofNullable(runs.get(runId)));
    }

    @Override
    public Promise<List<WorkflowRun>> findByWorkflowId(@NotNull String workflowId) {
        Objects.requireNonNull(workflowId, "workflowId");
        return Promise.of(
                runs.values().stream()
                        .filter(r -> workflowId.equals(r.workflowId()))
                        .toList());
    }

    @Override
    public Promise<List<WorkflowRun>> findByStatus(@NotNull WorkflowRunStatus status) {
        Objects.requireNonNull(status, "status");
        return Promise.of(
                runs.values().stream()
                        .filter(r -> status == r.status())
                        .toList());
    }

    @Override
    public Promise<List<WorkflowRun>> findExpiredWaits(@NotNull Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        return Promise.of(
                runs.values().stream()
                        .filter(r -> r.status() == WorkflowRunStatus.WAITING)
                        .filter(r -> r.startedAt().isBefore(cutoff))
                        .toList());
    }

    @Override
    public Promise<Void> updateStatus(@NotNull String runId, @NotNull WorkflowRunStatus status) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(status, "status");
        runs.computeIfPresent(runId, (id, existing) -> existing.withStatus(status));
        return Promise.complete();
    }

    @Override
    public void delete(@NotNull String runId) {
        Objects.requireNonNull(runId, "runId");
        runs.remove(runId);
    }

    /** Returns the number of runs currently stored (for testing). */
    public int size() {
        return runs.size();
    }

    /** Removes all stored runs (for testing). */
    public void clear() {
        runs.clear();
    }

    @Override
    public Promise<Void> storeMetrics(@NotNull Object metrics) {
        return Promise.complete();
    }
}
