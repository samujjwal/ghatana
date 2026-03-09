package com.ghatana.agent.memory.store.taskstate;

import com.ghatana.agent.memory.model.taskstate.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * Manages the lifecycle of task states — creation, phase transitions,
 * checkpoint management, and reconciliation on resume.
 *
 * <p>Orchestrates {@link TaskStateStore} with validation rules and event emission.
 *
 * @doc.type class
 * @doc.purpose Task state lifecycle orchestration
 * @doc.layer agent-memory
 */
public class TaskStateLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(TaskStateLifecycleManager.class);

    private final TaskStateStore store;

    public TaskStateLifecycleManager(@NotNull TaskStateStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Creates a new task and initializes it with the first phase.
     */
    @NotNull
    public Promise<TaskState> initializeTask(
            @NotNull String taskId,
            @NotNull String agentId,
            @NotNull String description) {

        TaskState task = TaskState.builder()
                .taskId(taskId)
                .agentId(agentId)
                .description(description)
                .status(TaskLifecycleStatus.IN_PROGRESS)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        log.info("Initializing task: {} for agent {}", taskId, agentId);
        return store.createTask(task);
    }

    /**
     * Transitions a task phase and records a checkpoint.
     */
    @NotNull
    public Promise<TaskState> transitionPhase(
            @NotNull String taskId,
            @NotNull String phaseId,
            @NotNull String newStatus) {

        log.info("Transitioning task {} phase {} → {}", taskId, phaseId, newStatus);
        return store.updatePhase(taskId, phaseId, newStatus);
    }

    /**
     * Reports a blocker that prevents task progress.
     */
    @NotNull
    public Promise<TaskBlocker> reportBlocker(
            @NotNull String taskId,
            @NotNull String description,
            @NotNull String category) {

        TaskBlocker blocker = TaskBlocker.builder()
                .id(java.util.UUID.randomUUID().toString())
                .description(description)
                .severity(category)
                .reportedAt(Instant.now())
                .build();

        log.warn("Blocker reported for task {}: {}", taskId, description);
        return store.reportBlocker(taskId, blocker);
    }

    /**
     * Reconciles a task on resume, checking for environment changes.
     */
    @NotNull
    public Promise<ReconcileResult> reconcileOnResume(@NotNull String taskId) {
        log.info("Reconciling task {} on resume", taskId);
        return store.reconcileOnResume(taskId);
    }

    /**
     * Garbage-collects stale tasks.
     */
    @NotNull
    public Promise<Integer> garbageCollect(@NotNull Instant inactiveSince) {
        return store.garbageCollect(inactiveSince);
    }
}
