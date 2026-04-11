package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;

/**
 * A checkpoint snapshot within a task phase.
 *
 * @doc.type value-object
 * @doc.purpose Task checkpoint for resume capability
 * @doc.layer agent-memory
 */
@Value
@Builder
public class TaskCheckpoint {

    @NotNull String id;
    @NotNull String phaseId;
    @NotNull Map<String, Object> snapshot;
    @NotNull Instant createdAt;
    @Nullable String description;
    @Builder.Default boolean restorable = true;
}
