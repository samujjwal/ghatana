/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.learning.LearningDelta;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Default implementation of {@link EvaluationHarness}.
 *
 * <p>Dispatches each {@link EvaluationTestCase} to the first {@link EvaluationExecutor} whose
 * {@link EvaluationExecutor#supports(EvaluationType)} returns {@code true}. If no executor
 * matches, the test case is recorded as skipped.
 *
 * <p>The default executor registry (used when no executors are injected) includes:
 * <ul>
 *   <li>{@link SafetyEvaluationExecutor} — handles SAFETY, PROMPT_INJECTION</li>
 *   <li>{@link OutputContractEvaluationExecutor} — handles OUTPUT_CONTRACT</li>
 *   <li>{@link TraceReplayEvaluationExecutor} — handles TRACE_GRADE, ROLLBACK_RECOVERY, RECOVERY</li>
 *   <li>{@link VersionCompatibilityEvaluationExecutor} — handles VERSION_COMPATIBILITY, COMPATIBILITY</li>
 * </ul>
 *
 * <p>Additional executors can be supplied at construction time and are prepended (highest
 * priority) to the default list.
 *
 * @doc.type class
 * @doc.purpose Executor-dispatching evaluation harness implementation
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public class DefaultEvaluationHarness implements EvaluationHarness {

    private static final Logger log = LoggerFactory.getLogger(DefaultEvaluationHarness.class);

    private final List<EvaluationExecutor> executors;

    /**
     * Creates a harness with the default set of executors.
     */
    public DefaultEvaluationHarness() {
        this(List.of());
    }

    /**
     * Creates a harness with additional custom executors prepended to the default list.
     *
     * @param additionalExecutors custom executors (checked before defaults)
     */
    public DefaultEvaluationHarness(@NotNull List<EvaluationExecutor> additionalExecutors) {
        Objects.requireNonNull(additionalExecutors, "additionalExecutors must not be null");
        List<EvaluationExecutor> all = new ArrayList<>(additionalExecutors);
        all.add(new SafetyEvaluationExecutor());
        all.add(new OutputContractEvaluationExecutor());
        all.add(new TraceReplayEvaluationExecutor());
        all.add(new VersionCompatibilityEvaluationExecutor());
        this.executors = List.copyOf(all);
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

        // Dispatch each test case to the appropriate executor
        List<Promise<EvaluationResult.TestCaseResult>> casePromises = pack.testCases().stream()
                .map(testCase -> dispatchToExecutor(testCase, context))
                .toList();

        return Promises.toList(casePromises).map(results -> {
            Instant completedAt = Instant.now();
            int passed = (int) results.stream().filter(EvaluationResult.TestCaseResult::passed).count();
            int skipped = (int) results.stream()
                    .filter(r -> !r.passed() && r.errorMessage() != null
                            && r.errorMessage().startsWith("[SKIPPED]"))
                    .count();
            int total = results.size();
            int failed = total - passed - skipped;
            double score = total > 0 ? (double) passed / total : 0.0;

            Map<String, String> refs = Map.of(
                    "deltaId", delta.deltaId(),
                    "packId", pack.packId(),
                    "runId", runId,
                    "tenantId", context.tenantId(),
                    "skillId", context.skillId()
            );

            log.info("Evaluation run {} complete: {}/{} passed, {} skipped, score={:.2f}",
                    runId, passed, total, skipped, score);

            return new EvaluationResult(
                    runId,
                    pack.packId(),
                    pack.targetArtifactId(),
                    delta.deltaId(),
                    startedAt,
                    completedAt,
                    total,
                    passed,
                    failed,
                    skipped,
                    score,
                    results,
                    refs
            );
        });
    }

    /**
     * Dispatches a test case to the first matching executor.
     * If no executor handles the type, returns a skipped result.
     */
    @NotNull
    private Promise<EvaluationResult.TestCaseResult> dispatchToExecutor(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        for (EvaluationExecutor executor : executors) {
            if (executor.supports(testCase.type())) {
                return executor.execute(testCase, context)
                        .mapException(e -> {
                            log.error("Executor {} failed for testCase={}: {}",
                                    executor.getClass().getSimpleName(), testCase.caseId(), e.getMessage(), e);
                            return e;
                        })
                        .then(
                                result -> Promise.of(result),
                                error -> Promise.of(new EvaluationResult.TestCaseResult(
                                        testCase.caseId(),
                                        testCase.name(),
                                        false,
                                        "",
                                        "Executor error: " + error.getMessage(),
                                        0L
                                ))
                        );
            }
        }

        // No executor found — skip gracefully
        log.debug("No executor registered for EvaluationType={}, marking as skipped: {}",
                testCase.type(), testCase.caseId());
        return Promise.of(new EvaluationResult.TestCaseResult(
                testCase.caseId(),
                testCase.name(),
                false,
                "",
                "[SKIPPED] No executor registered for type " + testCase.type(),
                0L
        ));
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
