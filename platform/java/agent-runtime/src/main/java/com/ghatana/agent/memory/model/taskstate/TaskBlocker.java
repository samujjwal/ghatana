package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A blocker preventing task progress.
 *
 * @doc.type value-object
 * @doc.purpose Task blocker tracking
 * @doc.layer agent-memory
 */
@Value
@Builder
public class TaskBlocker {

    @NotNull String id;
    @NotNull String description;
    @NotNull String severity;
    @NotNull Instant reportedAt;
    @Nullable Instant resolvedAt;
    @Nullable String resolution;

    /** Whether this blocker has been resolved. */
    public boolean isResolved() {
        return resolvedAt != null;
    }
}
