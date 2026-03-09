package com.ghatana.agent.memory.model.procedure;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single step in a procedure/skill.
 *
 * @doc.type value-object
 * @doc.purpose Procedure step definition
 * @doc.layer agent-memory
 */
@Value
@Builder
public class ProcedureStep {

    /** Step ordering (1-based). */
    int ordinal;

    /** Human-readable description of what this step does. */
    @NotNull
    String description;

    /** Tool to invoke for this step (null if manual/LLM action). */
    @Nullable
    String toolName;

    /** Expected outcome of this step. */
    @Nullable
    String expectedOutcome;

    /** Step to fall back to on failure (0 = no fallback). */
    @Builder.Default
    int fallbackStep = 0;
}
