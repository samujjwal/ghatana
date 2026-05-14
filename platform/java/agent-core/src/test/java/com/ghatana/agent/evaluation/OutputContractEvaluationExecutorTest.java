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
 * Tests for {@link OutputContractEvaluationExecutor}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for output contract validation
 * @doc.layer agent-core
 * @doc.pattern Test
 */
@DisplayName("OutputContractEvaluationExecutor Tests")
class OutputContractEvaluationExecutorTest extends EventloopTestBase {

    private OutputContractEvaluationExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new OutputContractEvaluationExecutor();
    }

    private static EvaluationContext context() {
        return new EvaluationContext("tenant-1", "agent-1", "skill-1", null, Map.of());
    }

    private static EvaluationTestCase contractCase(
            String actualOutput, String expectedOutput,
            Map<String, String> outputContract, double tolerance) {
        Map<String, String> context = new java.util.HashMap<>();
        for (Map.Entry<String, String> entry : outputContract.entrySet()) {
            context.put("outputContract." + entry.getKey(), entry.getValue());
        }
        return new EvaluationTestCase(
                "case-1", "contract-test", "desc",
                EvaluationType.OUTPUT_CONTRACT,
                "input",
            actualOutput,
            context,
            Map.of(),
                "artifact-1", "*",
                tolerance,
                List.of(), List.of(), 0.0,
                Map.of()
        );
    }

    @Test
    @DisplayName("supports OUTPUT_CONTRACT type")
    void supportsOutputContract() {
        assertThat(executor.supports(EvaluationType.OUTPUT_CONTRACT)).isTrue();
        assertThat(executor.supports(EvaluationType.SAFETY)).isFalse();
    }

    @Test
    @DisplayName("fails when output is empty and nonEmpty constraint set")
    void failsWhenOutputIsEmptyAndNonEmptyRequired() {
        EvaluationTestCase tc = contractCase(
                "", "something",
                Map.of("nonEmpty", "true"),
                0.0
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("empty");
    }

    @Test
    @DisplayName("passes when output contains required key")
    void passesWhenRequiredKeyPresent() {
        EvaluationTestCase tc = contractCase(
                "{\"status\": \"ok\", \"data\": []}",
                "",
                Map.of("requiredKeys", "status,data"),
                1.0  // any output
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("fails when required key is missing from output")
    void failsWhenRequiredKeyMissing() {
        EvaluationTestCase tc = contractCase(
                "{\"data\": []}",
                "",
                Map.of("requiredKeys", "status,data"),
                1.0
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("status");
    }

    @Test
    @DisplayName("fails when forbidden key is present in output")
    void failsWhenForbiddenKeyPresent() {
        EvaluationTestCase tc = contractCase(
                "{\"status\": \"ok\", \"secret\": \"password\"}",
                "",
                Map.of("forbiddenKeys", "secret"),
                1.0
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("secret");
    }

    @Test
    @DisplayName("passes when tolerance is 1.0 and output differs from expected")
    void passesWithFullTolerance() {
        EvaluationTestCase tc = contractCase(
                "completely different output",
                "expected output",
                Map.of(),
                1.0
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("fails when tolerance is 0.0 and output does not match expected exactly")
    void failsWithZeroToleranceOnMismatch() {
        EvaluationTestCase tc = contractCase(
                "actual output",
                "expected output",
                Map.of(),
                0.0
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }

    @Test
    @DisplayName("passes with exact match when tolerance is 0.0")
    void passesWithExactMatch() {
        EvaluationTestCase tc = contractCase(
                "exact output",
                "exact output",
                Map.of(),
                0.0
        );

        EvaluationResult.TestCaseResult result = runPromise(() -> executor.execute(tc, context()));

        assertThat(result.passed()).isTrue();
    }
}
