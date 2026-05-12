/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.agent.release.AgentRelease;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;

/**
 * Decision about which execution mode to use for a task.
 *
 * @doc.type record
 * @doc.purpose Mode decision record with reasoning
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ModeDecision(
        @NotNull TaskClass taskClass,
        @NotNull ExecutionMode executionMode,
        @NotNull String reasoning,
        @NotNull Map<String, String> metadata
) {
    public ModeDecision {
        Objects.requireNonNull(taskClass, "taskClass must not be null");
        Objects.requireNonNull(executionMode, "executionMode must not be null");
        Objects.requireNonNull(reasoning, "reasoning must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        metadata = Map.copyOf(metadata);
    }

    /**
     * Creates a mode decision with minimal metadata.
     *
     * @param taskClass task classification
     * @param executionMode execution mode
     * @param reasoning explanation for the decision
     * @return mode decision
     */
    @NotNull
    public static ModeDecision of(
            @NotNull TaskClass taskClass,
            @NotNull ExecutionMode executionMode,
            @NotNull String reasoning
    ) {
        return new ModeDecision(taskClass, executionMode, reasoning, Map.of());
    }
}
