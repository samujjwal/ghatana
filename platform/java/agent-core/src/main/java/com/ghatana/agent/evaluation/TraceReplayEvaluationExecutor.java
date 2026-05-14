/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Evaluates {@link EvaluationType#TRACE_GRADE} test cases.
 *
 * <p>Validates that a candidate procedure produces well-formed execution traces when run against
 * the declared test input. Specifically checks:
 * <ul>
 *   <li><b>Tool call coverage</b>: every tool call declared in
 *       {@link EvaluationTestCase#expectedToolCalls()} must appear in
 *       {@link EvaluationTestCase#actualToolCalls()}, within the declared
 *       {@link EvaluationTestCase#toolCallTolerance()} (fraction of missing calls allowed).</li>
 *   <li><b>No extraneous high-risk tool calls</b>: calls found in {@code actualToolCalls} that are
 *       not declared in {@code expectedToolCalls} are flagged when tolerance is zero.</li>
 *   <li><b>Completeness</b>: the trace must declare at least one tool call or produce non-empty
 *       output unless the test case context contains {@code "traceGrade.allowEmpty=true"}.</li>
 * </ul>
 *
 * <p>For {@link EvaluationType#ROLLBACK_RECOVERY} the executor additionally verifies that
 * a rollback step is present in the expected tool calls.
 *
 * @doc.type class
 * @doc.purpose Trace quality and tool-call coverage evaluator
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class TraceReplayEvaluationExecutor implements EvaluationExecutor {

    private static final Logger log = LoggerFactory.getLogger(TraceReplayEvaluationExecutor.class);

    @Override
    public boolean supports(@NotNull EvaluationType type) {
        return type == EvaluationType.TRACE_GRADE
                || type == EvaluationType.ROLLBACK_RECOVERY
                || type == EvaluationType.RECOVERY;
    }

    @Override
    @NotNull
    public Promise<EvaluationResult.TestCaseResult> execute(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        long startMs = System.currentTimeMillis();
        try {
            TraceCheckOutcome outcome = checkTrace(testCase, context);
            long durationMs = System.currentTimeMillis() - startMs;

            if (outcome.passed()) {
                log.debug("Trace grade PASSED for testCase={} skill={}", testCase.caseId(), context.skillId());
                return Promise.of(new EvaluationResult.TestCaseResult(
                        testCase.caseId(),
                        testCase.name(),
                        true,
                        "Trace satisfies quality requirements",
                        "",
                        durationMs
                ));
            } else {
                log.warn("Trace grade FAILED for testCase={}: {}", testCase.caseId(), outcome.reason());
                return Promise.of(new EvaluationResult.TestCaseResult(
                        testCase.caseId(),
                        testCase.name(),
                        false,
                        "",
                        outcome.reason(),
                        durationMs
                ));
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("Trace replay executor threw for testCase={}", testCase.caseId(), e);
            return Promise.of(new EvaluationResult.TestCaseResult(
                    testCase.caseId(),
                    testCase.name(),
                    false,
                    "",
                    "Trace replay executor error: " + e.getMessage(),
                    durationMs
            ));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @NotNull
    private TraceCheckOutcome checkTrace(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        List<String> expected = testCase.expectedToolCalls();
        List<String> actual = testCase.actualToolCalls();
        double tolerance = testCase.toolCallTolerance();

        // Completeness: require non-empty output or at least one tool call unless explicitly waived
        String allowEmpty = testCase.context().get("traceGrade.allowEmpty");
        if (!"true".equalsIgnoreCase(allowEmpty)) {
            if (actual.isEmpty() && testCase.expectedOutput().isBlank()) {
                return TraceCheckOutcome.fail(
                        "Trace completeness check failed: no tool calls and no output produced");
            }
        }

        // Tool call coverage check
        if (!expected.isEmpty()) {
            long missingCount = expected.stream()
                    .filter(call -> !actual.contains(call))
                    .count();
            double missingFraction = (double) missingCount / expected.size();

            if (missingFraction > tolerance) {
                long firstMissing = expected.stream()
                        .filter(call -> !actual.contains(call))
                        .count();
                String firstMissingCall = expected.stream()
                        .filter(call -> !actual.contains(call))
                        .findFirst()
                        .orElse("unknown");
                return TraceCheckOutcome.fail(
                        String.format("Trace coverage %.0f%% below threshold %.0f%%: " +
                                        "first missing call='%s' (%d missing of %d expected)",
                                (1.0 - missingFraction) * 100,
                                (1.0 - tolerance) * 100,
                                firstMissingCall,
                                missingCount,
                                expected.size()));
            }
        }

        // No unexpected tool calls when tolerance is zero
        if (tolerance == 0.0 && !actual.isEmpty() && !expected.isEmpty()) {
            for (String actualCall : actual) {
                if (!expected.contains(actualCall)) {
                    return TraceCheckOutcome.fail(
                            "Trace contains unexpected tool call '" + actualCall
                                    + "' not declared in expectedToolCalls (tolerance=0)");
                }
            }
        }

        // ROLLBACK_RECOVERY: rollback step must be present in expected tool calls
        if (testCase.type() == EvaluationType.ROLLBACK_RECOVERY) {
            boolean hasRollback = expected.stream()
                    .anyMatch(call -> call.toLowerCase().contains("rollback")
                            || call.toLowerCase().contains("revert")
                            || call.toLowerCase().contains("undo"));
            if (!hasRollback) {
                return TraceCheckOutcome.fail(
                        "ROLLBACK_RECOVERY test case does not declare a rollback step in expectedToolCalls");
            }
        }

        return TraceCheckOutcome.pass();
    }

    private record TraceCheckOutcome(boolean passed, String reason) {
        static TraceCheckOutcome pass() {
            return new TraceCheckOutcome(true, "");
        }

        static TraceCheckOutcome fail(String reason) {
            return new TraceCheckOutcome(false, reason);
        }
    }
}
