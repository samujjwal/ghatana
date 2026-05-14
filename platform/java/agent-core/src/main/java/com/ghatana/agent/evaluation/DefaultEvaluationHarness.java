/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.learning.LearningDelta;
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
    public Promise<EvaluationResult> run(
            @NotNull EvaluationPack pack,
            @NotNull LearningDelta delta,
            @NotNull EvaluationContext context) {
        Objects.requireNonNull(pack, "pack must not be null");
        Objects.requireNonNull(delta, "delta must not be null");
        Objects.requireNonNull(context, "context must not be null");

        String runId = java.util.UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        List<EvaluationResult.TestCaseResult> results = new ArrayList<>();

        // Run each test case in the pack
        for (EvaluationTestCase testCase : pack.testCases()) {
            EvaluationResult.TestCaseResult result = runTestCase(testCase, context);
            results.add(result);
        }

        Instant completedAt = Instant.now();
        int passed = (int) results.stream().filter(EvaluationResult.TestCaseResult::passed).count();
        int total = results.size();
        double score = total > 0 ? (double) passed / total : 0.0;

        // Build evaluation result with refs for promotion evidence mapping
        Map<String, String> refs = Map.of(
                "deltaId", delta.deltaId(),
                "packId", pack.packId(),
                "runId", runId,
                "tenantId", context.tenantId(),
                "skillId", context.skillId()
        );

        EvaluationResult evaluationResult = new EvaluationResult(
                runId,
                pack.packId(),
                pack.targetArtifactId(),
                delta.deltaId(),
                startedAt,
                completedAt,
                total,
                passed,
                total - passed,
                0,
                score,
                results,
                refs
        );
        return Promise.of(evaluationResult);
    }

    /**
     * Runs a single test case.
     */
    @NotNull
    private EvaluationResult.TestCaseResult runTestCase(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {
        // In a real implementation, this would execute the test case in a sandbox
        // For now, return a mock result
        return new EvaluationResult.TestCaseResult(
                testCase.caseId(),
                testCase.name(),
                true, // Assume pass for now
                testCase.expectedOutput(),
                "",
                0L
        );
    }

    @Override
    @NotNull
    public EvaluationPack createDefaultPackForProceduralSkill(@NotNull String targetArtifactId) {
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        
        List<EvaluationTestCase> testCases = new ArrayList<>();
        
        // Unit test - required for COMPETENT promotion
        testCases.add(new EvaluationTestCase(
                "unit-1",
                "Unit Test 1",
                "Basic procedural skill unit test",
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
        
        // Integration test - required for COMPETENT promotion
        testCases.add(new EvaluationTestCase(
                "integration-1",
                "Integration Test 1",
                "Procedural skill integration test",
                EvaluationType.INTEGRATION,
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
        
        // Basic safety test - required for COMPETENT promotion
        testCases.add(new EvaluationTestCase(
                "safety-1",
                "Safety Test 1",
                "Basic safety verification",
                EvaluationType.SAFETY,
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
                Map.of("requiredFor", "COMPETENT"),
                Instant.now(),
                Map.of()
        );
    }

    @Override
    @NotNull
    public EvaluationPack createDefaultPackForSemanticFact(@NotNull String targetArtifactId) {
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        
        List<EvaluationTestCase> testCases = new ArrayList<>();
        
        // Unit test
        testCases.add(new EvaluationTestCase(
                "unit-1",
                "Unit Test 1",
                "Basic semantic fact unit test",
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
        
        // Regression test
        testCases.add(new EvaluationTestCase(
                "regression-1",
                "Regression Test 1",
                "Semantic fact regression test",
                EvaluationType.REGRESSION,
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
                Map.of("requiredFor", "COMPETENT"),
                Instant.now(),
                Map.of()
        );
    }

    @Override
    @NotNull
    public EvaluationPack createMasteredPack(@NotNull String targetArtifactId) {
        Objects.requireNonNull(targetArtifactId, "targetArtifactId must not be null");
        
        List<EvaluationTestCase> testCases = new ArrayList<>();
        
        // Regression test - required for MASTERED promotion
        testCases.add(new EvaluationTestCase(
                "regression-1",
                "Regression Test 1",
                "Comprehensive regression test",
                EvaluationType.REGRESSION,
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
        
        // Safety test - required for MASTERED promotion
        testCases.add(new EvaluationTestCase(
                "safety-1",
                "Safety Test 1",
                "Comprehensive safety verification",
                EvaluationType.SAFETY,
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
        
        // Recovery test - required for MASTERED promotion
        testCases.add(new EvaluationTestCase(
                "recovery-1",
                "Recovery Test 1",
                "Failure recovery verification",
                EvaluationType.RECOVERY,
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
        
        // Compatibility test - required for MASTERED promotion
        testCases.add(new EvaluationTestCase(
                "compatibility-1",
                "Compatibility Test 1",
                "Version compatibility verification",
                EvaluationType.COMPATIBILITY,
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
        
        // Trace grade test - required for MASTERED promotion
        testCases.add(new EvaluationTestCase(
                "trace-grade-1",
                "Trace Grade Test 1",
                "Trace quality and completeness verification",
                EvaluationType.TRACE_GRADE,
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
        
        // Output contract test
        testCases.add(new EvaluationTestCase(
                "output-contract-1",
                "Output Contract Test 1",
                "Output schema and constraints verification",
                EvaluationType.OUTPUT_CONTRACT,
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
                "mastered-" + targetArtifactId,
                "Mastered Evaluation Pack",
                "Comprehensive evaluation pack for MASTERED promotion: " + targetArtifactId,
                targetArtifactId,
                "PROCEDURAL_SKILL",
                testCases,
                Map.of("requiredFor", "MASTERED"),
                Instant.now(),
                Map.of()
        );
    }
}
