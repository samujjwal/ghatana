/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

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
     * Runs an evaluation pack against a target artifact.
     *
     * @param pack evaluation pack to run
     * @param deltaId learning delta ID being evaluated
     * @return promise of evaluation result
     */
    @NotNull
    Promise<EvaluationResult> run(@NotNull EvaluationPack pack, @NotNull String deltaId);

    /**
     * Creates a default evaluation pack for a procedural skill.
     *
     * @param targetArtifactId target artifact ID
     * @return evaluation pack
     */
    @NotNull
    EvaluationPack createDefaultPackForProceduralSkill(@NotNull String targetArtifactId);

    /**
     * Creates a default evaluation pack for a semantic fact.
     *
     * @param targetArtifactId target artifact ID
     * @return evaluation pack
     */
    @NotNull
    EvaluationPack createDefaultPackForSemanticFact(@NotNull String targetArtifactId);
}
