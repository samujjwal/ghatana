/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.learning.LearningDelta;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Harness for running evaluation packs in a sandboxed environment.
 *
 * @doc.type interface
 * @doc.purpose Harness for running evaluation packs
 * @doc.layer agent-core
 * @doc.pattern Harness
 */
public interface EvaluationHarness {

    /**
     * Runs an evaluation pack against a learning delta with evaluation context.
     *
     * @param pack evaluation pack to run
     * @param delta learning delta being evaluated
     * @param context evaluation context (tenant, agent, version, etc.)
     * @return promise of evaluation result
     */
    @NotNull
    Promise<EvaluationResult> run(
            @NotNull EvaluationPack pack,
            @NotNull LearningDelta delta,
            @NotNull EvaluationContext context);

    /**
     * Creates a default evaluation pack for a procedural skill.
     * Includes unit, integration, and basic safety tests required for COMPETENT promotion.
     *
     * @param targetArtifactId target artifact ID
     * @return evaluation pack
     */
    @NotNull
    EvaluationPack createDefaultPackForProceduralSkill(@NotNull String targetArtifactId);

    /**
     * Creates a default evaluation pack for a semantic fact.
     * Includes unit and regression tests.
     *
     * @param targetArtifactId target artifact ID
     * @return evaluation pack
     */
    @NotNull
    EvaluationPack createDefaultPackForSemanticFact(@NotNull String targetArtifactId);

    /**
     * Creates a comprehensive evaluation pack for MASTERED promotion.
     * Includes regression, safety, recovery, compatibility, and trace-grade tests.
     *
     * @param targetArtifactId target artifact ID
     * @return evaluation pack
     */
    @NotNull
    EvaluationPack createMasteredPack(@NotNull String targetArtifactId);
}

/**
 * Evaluation context providing execution environment information.
 *
 * @param tenantId tenant identifier
 * @param agentId agent identifier
 * @param skillId skill identifier
 * @param versionContext version context for compatibility checks
 * @param metadata additional context metadata
 */
record EvaluationContext(
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
