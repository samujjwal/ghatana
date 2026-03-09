package com.ghatana.agent.memory.store.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A conflict detected during resume reconciliation.
 *
 * @doc.type value-object
 * @doc.purpose Reconciliation conflict
 * @doc.layer agent-memory
 */
@Value
@Builder
public class Conflict {

    @NotNull String field;
    @NotNull String storedValue;
    @NotNull String currentValue;
    @NotNull String severity;
    boolean autoResolvable;
}
