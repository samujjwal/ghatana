/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TraceReplayEvaluationExecutor}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for trace quality and tool-call coverage evaluation
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("TraceReplayEvaluationExecutor Tests")
class TraceReplayEvaluationExecutorTest extends EventloopTestBase {

    private TraceReplayEvaluationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TraceReplayEvaluationExecutor();
    }

    private static EvaluationContext context() {
        return new EvaluationContext("tenant-1", "agent-1", "skill-1", null, Map.of());
    }

    private static EvaluationTestCase traceCase(
            EvaluationType type,
            String actualOutput,
            List<String> expectedTools,
            List<String> actualTools,
            double toolCallTolerance,
            Map<String, String> traceContext) {
        return new EvaluationTestCase(
                "case-1", "trace-test", "desc",
                type,
            "input", actualOutput,
                traceContext,
                Map.of(),
                "artifact-1", "*",
                0.5,
                expectedTools, actualTools, toolCallTolerance,
                Map.of()
        );
    }

    @Test
    @DisplayName("supports TRACE_GRADE, ROLLBACK_RECOVERY, RECOVERY types")
    void supportsCorrectTypes() {
        assertThat(executor.supports(EvaluationType.TRACE_GRADE)).isTrue();
        assertThat(executor.supports(EvaluationType.ROLLBACK_RECOVERY)).isTrue();
        assertThat(executor.supports(EvaluationType.RECOVERY)).isTrue();
        assertThat(executor.supports(EvaluationType.SAFETY)).isFalse();
    }

    @Test
    @DisplayName("passes when all expected tool calls are present in actual")
    void passesWhenToolCallsCovered() {
        EvaluationTestCase tc = traceCase(
                EvaluationType.TRACE_GRADE,
                "some output",
                List.of("tool_a", "tool_b"),
                List.of("tool_a", "tool_b"),
                0.0,
                Map.of("output", "some output")
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("fails when zero tolerance and expected tool is missing")
    void failsWhenToolMissingWithZeroTolerance() {
        EvaluationTestCase tc = traceCase(
                EvaluationType.TRACE_GRADE,
                "output",
                List.of("tool_a", "tool_b"),
                List.of("tool_a"),  // missing tool_b
                0.0,
                Map.of("output", "output")
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("tool_b");
    }

    @Test
    @DisplayName("passes with sufficient tool call tolerance despite missing one tool")
    void passesWithSufficientTolerance() {
        EvaluationTestCase tc = traceCase(
                EvaluationType.TRACE_GRADE,
                "output",
                List.of("tool_a", "tool_b"),
                List.of("tool_a"),  // 50% coverage
                0.5,                // 50% tolerance — 1/2 covered meets threshold
                Map.of("output", "output")
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("fails when ROLLBACK_RECOVERY requires rollback step that is absent in tool calls")
    void failsWhenRollbackStepMissing() {
        EvaluationTestCase tc = new EvaluationTestCase(
                "rollback-case", "rollback-test", "desc",
                EvaluationType.ROLLBACK_RECOVERY,
                "input", "expected",
                Map.of("output", "recovered"),
                Map.of("rollbackRequired", "true"),
                "artifact-1", "*",
                0.5,
                List.of("deploy", "rollback"),
                List.of("deploy"),  // rollback step missing
                0.0,
                Map.of()
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("rollback");
    }

    @Test
    @DisplayName("fails when output is empty and not explicitly allowed")
    void failsWhenOutputIsEmpty() {
        EvaluationTestCase tc = traceCase(
                EvaluationType.TRACE_GRADE,
                "",
                List.of(),
                List.of(),
                1.0,
                Map.of("output", "")
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isFalse();
    }

    @Test
    @DisplayName("passes when output is empty and allowEmpty traceGrade setting is true")
    void passesWhenOutputIsEmptyAndAllowEmptySet() {
        EvaluationTestCase tc = new EvaluationTestCase(
                "allow-empty", "allow-empty-test", "desc",
                EvaluationType.TRACE_GRADE,
                "input", "",
            Map.of("traceGrade.allowEmpty", "true", "output", ""),
            Map.of(),
                "artifact-1", "*",
                0.5,
                List.of(), List.of(), 1.0,
                Map.of()
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }
}
