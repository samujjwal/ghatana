package com.ghatana.agent.memory.model.procedure;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Version history entry for a procedure/skill.
 *
 * @doc.type value-object
 * @doc.purpose Procedure version tracking
 * @doc.layer agent-memory
 */
@Value
@Builder
public class ProcedureVersion {

    /** Version number. */
    int version;

    /** Steps at this version. */
    @NotNull
    List<ProcedureStep> steps;

    /** Success rate of this version. */
    double successRate;

    /** Number of times this version was used. */
    int usageCount;

    /** When this version was promoted to production. */
    @Nullable
    Instant promotedAt;

    /** Summary of evaluation results that led to promotion. */
    @Nullable
    String evaluationResult;
}
