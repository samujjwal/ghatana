package com.ghatana.agent.memory.store.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Result of a resume reconciliation check.
 *
 * @doc.type value-object
 * @doc.purpose Reconciliation result for task resume
 * @doc.layer agent-memory
 */
@Value
@Builder
public class ReconcileResult {

    @Nullable String taskId;
    @NotNull List<Conflict> conflicts;
    int autoResolved;
    boolean requiresHumanReview;
    boolean resumable;
    @Builder.Default @NotNull List<String> recommendations = List.of();
}
