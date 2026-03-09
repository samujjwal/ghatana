package com.ghatana.agent.memory.model.taskstate;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * Snapshot of the environment at a point in time.
 * Used for reconciliation on task resume.
 *
 * @doc.type value-object
 * @doc.purpose Environment state capture for resume reconciliation
 * @doc.layer agent-memory
 */
@Value
@Builder
public class EnvironmentSnapshot {

    @NotNull Instant capturedAt;
    @NotNull Map<String, String> properties;
    @Builder.Default @NotNull Map<String, String> checksums = Map.of();
}
