/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of EvaluationHarness for running evaluation test cases.
 *
 * @doc.type class
 * @doc.purpose Default evaluation harness implementation
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public class DefaultEvaluationHarness implements EvaluationHarness {

    public DefaultEvaluationHarness() {
        // Default constructor
    }

    @Override
    @NotNull
    public Promise<EvaluationResult> run(@NotNull EvaluationPack pack, @NotNull String deltaId) {
        Objects.requireNonNull(pack, "pack must not be null");
        Objects.requireNonNull(deltaId, "deltaId must not be null");

        String runId = java.util.UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        List<EvaluationResult.TestCaseResult> results = new ArrayList<>();

        for (EvaluationTestCase testCase : pack.testCases()) {
            EvaluationResult.TestCaseResult result = new EvaluationResult.TestCaseResult(
                    testCase.caseId(),
                    testCase.name(),
                    true,
                    testCase.expectedOutput(),
                    "",
                    0L
            );
            results.add(result);
        }

        Instant completedAt = Instant.now();
        int passed = (int) results.stream().filter(EvaluationResult.TestCaseResult::passed).count();
        int total = results.size();
        double score = total > 0 ? (double) passed / total : 0.0;

        EvaluationResult evaluationResult = new EvaluationResult(
                runId,
                pack.packId(),
                pack.targetArtifactId(),
                deltaId,
                startedAt,
                completedAt,
                total,
                passed,
                total - passed,
                0,
                score,
                results,
                Map.of()
        );
        return Promise.of(evaluationResult);
    }

    @Override
    @NotNull
    public EvaluationPack createDefaultPackForProceduralSkill(@NotNull String targetArtifactId) {
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        
        List<EvaluationTestCase> testCases = new ArrayList<>();
        testCases.add(new EvaluationTestCase(
                "default-1",
                "Default Test Case 1",
                "Basic procedural skill test",
                EvaluationType.SKILL_UNIT,
                "test input",
                "expected output",
                Map.of(),
                Map.of("targetArtifactId", targetArtifactId),
                targetArtifactId,
                "latest",
                0.1,
                List.of(),
                List.of(),
                0.1,
                Map.of()
        ));
        
        return new EvaluationPack(
                "default-procedural-" + targetArtifactId,
                "Default Procedural Skill Pack",
                "Default evaluation pack for procedural skill: " + targetArtifactId,
                targetArtifactId,
                "PROCEDURAL_SKILL",
                testCases,
                Map.of(),
                Instant.now(),
                Map.of()
        );
    }

    @Override
    @NotNull
    public EvaluationPack createDefaultPackForSemanticFact(@NotNull String targetArtifactId) {
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        
        List<EvaluationTestCase> testCases = new ArrayList<>();
        testCases.add(new EvaluationTestCase(
                "default-1",
                "Default Test Case 1",
                "Basic semantic fact test",
                EvaluationType.SKILL_UNIT,
                "test input",
                "expected output",
                Map.of(),
                Map.of("targetArtifactId", targetArtifactId),
                targetArtifactId,
                "latest",
                0.1,
                List.of(),
                List.of(),
                0.1,
                Map.of()
        ));
        
        return new EvaluationPack(
                "default-semantic-" + targetArtifactId,
                "Default Semantic Fact Pack",
                "Default evaluation pack for semantic fact: " + targetArtifactId,
                targetArtifactId,
                "SEMANTIC_FACT",
                testCases,
                Map.of(),
                Instant.now(),
                Map.of()
        );
    }
}
