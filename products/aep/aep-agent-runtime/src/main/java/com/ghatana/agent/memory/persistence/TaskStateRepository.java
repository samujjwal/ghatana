package com.ghatana.agent.memory.persistence;

import com.ghatana.agent.memory.model.taskstate.TaskState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * JDBC-based persistence interface for task states.
 *
 * @doc.type interface
 * @doc.purpose Task state persistence SPI
 * @doc.layer agent-memory
 */
public interface TaskStateRepository {

    @NotNull Promise<TaskState> save(@NotNull TaskState task);

    @NotNull Promise<@Nullable TaskState> findById(@NotNull String taskId);

    @NotNull Promise<List<TaskState>> findActiveByAgent(@NotNull String agentId);

    @NotNull Promise<Void> updateStatus(@NotNull String taskId, @NotNull String status);

    @NotNull Promise<Void> archive(@NotNull String taskId);

    @NotNull Promise<Integer> archiveInactiveSince(@NotNull Instant since);
}
