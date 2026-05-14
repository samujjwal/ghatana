/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Evaluation context providing execution environment information for evaluation runs.
 *
 * @param tenantId tenant identifier
 * @param agentId agent identifier
 * @param skillId skill identifier
 * @param versionContext version context for compatibility checks
 * @param metadata additional context metadata
 *
 * @doc.type record
 * @doc.purpose Evaluation context for harness and executor runs
 * @doc.layer agent-core
 * @doc.pattern ValueObject
 */
public record EvaluationContext(
        @NotNull String tenantId,
        @NotNull String agentId,
        @NotNull String skillId,
        @Nullable com.ghatana.agent.context.version.VersionContext versionContext,
        @NotNull Map<String, String> metadata
) {
    public EvaluationContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        if (skillId == null || skillId.isBlank()) {
            throw new IllegalArgumentException("skillId must not be blank");
        }
        metadata = Map.copyOf(metadata);
    }
}
