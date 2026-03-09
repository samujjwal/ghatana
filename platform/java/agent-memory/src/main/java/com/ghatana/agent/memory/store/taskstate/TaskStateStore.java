package com.ghatana.agent.memory.store.taskstate;

import com.ghatana.agent.memory.model.taskstate.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Store for task-state memory — manages multi-session workflow state
 * including phases, checkpoints, blockers, and resume reconciliation.
 *
 * @doc.type interface
 * @doc.purpose Task state persistence SPI
 * @doc.layer agent-memory
 */
public interface TaskStateStore {

    @NotNull Promise<TaskState> createTask(@NotNull TaskState task);

    @NotNull Promise<@Nullable TaskState> getTask(@NotNull String taskId);

    @NotNull Promise<TaskState> updatePhase(
            @NotNull String taskId,
            @NotNull String phaseId,
            @NotNull String status);

    @NotNull Promise<TaskCheckpoint> addCheckpoint(
            @NotNull String taskId,
            @NotNull TaskCheckpoint checkpoint);

    @NotNull Promise<TaskBlocker> reportBlocker(
            @NotNull String taskId,
            @NotNull TaskBlocker blocker);

    @NotNull Promise<TaskBlocker> resolveBlocker(
            @NotNull String taskId,
            @NotNull String blockerId,
            @NotNull String resolution);

    /**
     * Reconciles stored task state against current environment on resume.
     *
     * @param taskId Task to reconcile
     * @return Reconciliation result with conflicts and recommendations
     */
    @NotNull Promise<ReconcileResult> reconcileOnResume(@NotNull String taskId);

    @NotNull Promise<Void> archiveTask(@NotNull String taskId);

    @NotNull Promise<List<TaskState>> listActiveTasks(@NotNull String agentId);

    /**
     * Garbage-collects tasks inactive since the given timestamp.
     *
     * @param inactiveSince Threshold
     * @return Number of tasks archived
     */
    @NotNull Promise<Integer> garbageCollect(@NotNull Instant inactiveSince);
}
