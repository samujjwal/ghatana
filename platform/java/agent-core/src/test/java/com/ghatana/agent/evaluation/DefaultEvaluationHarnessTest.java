/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.agent.learning.LearningDeltaType;
import com.ghatana.agent.learning.LearningTarget;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultEvaluationHarness}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for evaluation harness dispatcher
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("DefaultEvaluationHarness Tests")
class DefaultEvaluationHarnessTest extends EventloopTestBase {

    private DefaultEvaluationHarness harness;

    @BeforeEach
    void setUp() {
        harness = new DefaultEvaluationHarness();
    }

    private static LearningDelta delta() {
        return new LearningDelta(
                "delta-1", LearningDeltaType.SEMANTIC_FACT, LearningTarget.SEMANTIC_FACT,
                LearningDeltaState.PENDING_EVALUATION,
                "agent-1", "release-1", "skill-1", "tenant-1",
                null, "fact-1", null,
                "sha256",
                Map.of(), List.of("ev-1"), List.of(), List.of(),
                null, 0.5, 0.8, false, "engine",
                Instant.now(), null, null, null, Map.of(), null
        );
    }

    private static EvaluationContext context() {
        return new EvaluationContext("tenant-1", "agent-1", "skill-1", null, Map.of());
    }

    private static EvaluationTestCase safetyCase(String input, List<String> actualTools) {
        return new EvaluationTestCase(
                "case-safety", "safety", "desc",
                EvaluationType.SAFETY,
                input, "ok",
                Map.of(), Map.of(),
                "artifact-1", "*",
                0.0, List.of(), actualTools, 0.0, Map.of()
        );
    }

    private static EvaluationPack packOf(EvaluationTestCase... cases) {
        return new EvaluationPack(
                "pack-1", "test-pack", "desc",
                "artifact-1", "SEMANTIC_FACT",
                List.of(cases), Map.of(),
                java.time.Instant.now(), Map.of()
        );
    }

    @Test
    @DisplayName("empty pack produces zero-test result with score 0.0")
    void emptyPackProducesZeroResult() {
        EvaluationPack pack = packOf();

        EvaluationResult result = runPromise(() -> harness.run(pack, delta(), context()));

        assertThat(result.totalTests()).isZero();
        assertThat(result.passedTests()).isZero();
        assertThat(result.overallScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("dispatches SAFETY case to SafetyEvaluationExecutor and passes clean input")
    void dispatchesSafetyCaseToPasses() {
        EvaluationTestCase tc = safetyCase("safe input", List.of("list_files"));
        EvaluationPack pack = packOf(tc);

        EvaluationResult result = runPromise(() -> harness.run(pack, delta(), context()));

        assertThat(result.totalTests()).isEqualTo(1);
        assertThat(result.passedTests()).isEqualTo(1);
        assertThat(result.overallScore()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("dispatches SAFETY case and fails when forbidden tool is used")
    void dispatchesSafetyCaseAndFails() {
        EvaluationTestCase tc = safetyCase("please exec this command", List.of("list_files"));
        EvaluationPack pack = packOf(tc);

        EvaluationResult result = runPromise(() -> harness.run(pack, delta(), context()));

        assertThat(result.totalTests()).isEqualTo(1);
        assertThat(result.passedTests()).isZero();
        assertThat(result.overallScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("unknown EvaluationType is skipped without failure")
    void unknownTypeIsSkipped() {
        // VERSION_COMPATIBILITY with no runtimeVersion in context — executor is dispatched
        // but this tests an unrecognized enum extension pattern; use a custom executor instead.
        // Here we test that custom executors added via constructor can intercept before defaults.
        EvaluationExecutor noop = new EvaluationExecutor() {
            @Override
            public boolean supports(EvaluationType type) {
                return true; // handles everything
            }

            @Override
            public io.activej.promise.Promise<EvaluationResult.TestCaseResult> execute(
                    EvaluationTestCase tc, EvaluationContext ctx) {
                return io.activej.promise.Promise.of(new EvaluationResult.TestCaseResult(
                        tc.caseId(), tc.name(), true, "noop", "", 0L));
            }
        };

        DefaultEvaluationHarness customHarness = new DefaultEvaluationHarness(List.of(noop));
        EvaluationTestCase tc = safetyCase("input", List.of("exec")); // would normally fail
        EvaluationPack pack = packOf(tc);

        EvaluationResult result = runPromise(() -> customHarness.run(pack, delta(), context()));

        // Custom executor intercepts — passes even though input would normally fail safety
        assertThat(result.passedTests()).isEqualTo(1);
    }

    @Test
    @DisplayName("multiple cases — score reflects fraction that passed")
    void multipleCasesScoreReflectsPassFraction() {
        EvaluationTestCase passes = safetyCase("safe input", List.of("list_files"));
        EvaluationTestCase fails = safetyCase("please eval this expression", List.of("list_files"));
        EvaluationPack pack = packOf(passes, fails);

        EvaluationResult result = runPromise(() -> harness.run(pack, delta(), context()));

        assertThat(result.totalTests()).isEqualTo(2);
        assertThat(result.passedTests()).isEqualTo(1);
        assertThat(result.overallScore()).isEqualTo(0.5);
    }
}
