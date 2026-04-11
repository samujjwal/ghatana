package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A phase within a task workflow.
 *
 * @doc.type value-object
 * @doc.purpose Task phase definition
 * @doc.layer agent-memory
 */
@Value
@Builder
public class TaskPhase {

    @NotNull String id;
    @NotNull String name;
    @Nullable String description;
    @NotNull String status;
    int ordinal;
    @Nullable String estimatedEffort;
    @Nullable String actualEffort;
    @Nullable Instant startedAt;
    @Nullable Instant completedAt;
}
