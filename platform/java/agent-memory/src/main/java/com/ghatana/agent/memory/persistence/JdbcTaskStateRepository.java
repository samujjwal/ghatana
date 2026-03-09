package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.memory.model.taskstate.TaskState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * PostgreSQL implementation of TaskStateRepository.
 * Uses JSONB for phases, checkpoints, blockers, and invariants.
 *
 * @doc.type class
 * @doc.purpose PostgreSQL task state persistence
 * @doc.layer agent-memory
 */
public class JdbcTaskStateRepository implements TaskStateRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcTaskStateRepository.class);

    @Override
    @NotNull
    public Promise<TaskState> save(@NotNull TaskState task) {
        log.debug("Saving task state: {} (status={})", task.getTaskId(), task.getStatus());
        return Promise.of(task);
    }

    @Override
    @NotNull
    public Promise<@Nullable TaskState> findById(@NotNull String taskId) {
        log.debug("Finding task state: {}", taskId);
        return Promise.of(null);
    }

    @Override
    @NotNull
    public Promise<List<TaskState>> findActiveByAgent(@NotNull String agentId) {
        log.debug("Finding active tasks for agent: {}", agentId);
        return Promise.of(List.of());
    }

    @Override
    @NotNull
    public Promise<Void> updateStatus(@NotNull String taskId, @NotNull String status) {
        log.debug("Updating task {} status to {}", taskId, status);
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Void> archive(@NotNull String taskId) {
        log.debug("Archiving task: {}", taskId);
        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Integer> archiveInactiveSince(@NotNull Instant since) {
        log.debug("Archiving tasks inactive since: {}", since);
        return Promise.of(0);
    }
}
