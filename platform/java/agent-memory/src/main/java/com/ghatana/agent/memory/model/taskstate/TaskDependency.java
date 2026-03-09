package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A dependency between tasks.
 *
 * @doc.type value-object
 * @doc.purpose Task dependency tracking
 * @doc.layer agent-memory
 */
@Value
@Builder
public class TaskDependency {

    @NotNull String dependsOnTaskId;
    @NotNull Type type;
    @NotNull Status status;

    public enum Type {
        /** Blocks progress until dependency is resolved. */
        BLOCKS,
        /** Provides information but doesn't block. */
        INFORMS
    }

    public enum Status {
        PENDING,
        SATISFIED,
        FAILED
    }
}
