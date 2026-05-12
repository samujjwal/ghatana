/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.trace;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A trace representing a sequence of actions and observations.
 *
 * @doc.type record
 * @doc.purpose Trace representing a sequence of actions and observations
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record Trace(
        @NotNull String traceId,
        @NotNull String agentId,
        @NotNull String turnId,
        @NotNull String input,
        @NotNull String output,
        @NotNull List<TraceStep> steps,
        @NotNull Map<String, String> metadata,
        @NotNull Instant createdAt
) {
    public Trace {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(turnId, "turnId must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(output, "output must not be null");
        Objects.requireNonNull(steps, "steps must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        steps = List.copyOf(steps);
        metadata = Map.copyOf(metadata);
    }

    /**
     * A single step in a trace.
     */
    public record TraceStep(
            @NotNull String stepId,
            @NotNull String action,
            @NotNull String observation,
            @NotNull Map<String, String> metadata
    ) {
        public TraceStep {
            Objects.requireNonNull(stepId, "stepId must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(observation, "observation must not be null");
            Objects.requireNonNull(metadata, "metadata must not be null");
            metadata = Map.copyOf(metadata);
        }
    }
}
