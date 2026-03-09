package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * An invariant condition that must remain satisfied throughout a task.
 *
 * @doc.type value-object
 * @doc.purpose Task invariant validation
 * @doc.layer agent-memory
 */
@Value
@Builder
public class TaskInvariant {

    @NotNull String id;
    @NotNull String description;
    @NotNull String checkExpression;
    @Nullable Instant lastCheckedAt;
    @Builder.Default boolean satisfied = true;
}
