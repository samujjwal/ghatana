package com.ghatana.agent.memory.model.fact;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Version history entry for a semantic fact.
 * Each update to a fact creates a new version while preserving history.
 *
 * @doc.type value-object
 * @doc.purpose Fact version tracking
 * @doc.layer agent-memory
 */
@Value
@Builder
public class FactVersion {

    /** Version number (monotonically increasing). */
    int version;

    /** Content of the fact at this version (typically the object value). */
    @NotNull
    String content;

    /** When this version was created. */
    @NotNull
    Instant changedAt;

    /** Who/what created this version (agent ID or system process). */
    @NotNull
    String changedBy;

    /** Reason for the change. */
    @Nullable
    String changeReason;
}
