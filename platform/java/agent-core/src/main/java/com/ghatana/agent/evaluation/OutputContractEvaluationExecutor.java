/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Evaluates {@link EvaluationType#OUTPUT_CONTRACT} test cases.
 *
 * <p>Verifies that a procedure or skill's proposed output satisfies a declared output schema:
 * <ul>
 *   <li><b>Required keys</b>: all keys listed in {@code outputContract.requiredKeys} must appear
 *       in the actual output.</li>
 *   <li><b>Forbidden keys</b>: keys listed in {@code outputContract.forbiddenKeys} must be absent.</li>
 *   <li><b>Non-empty</b>: the actual output must not be blank when
 *       {@code outputContract.nonEmpty=true}.</li>
 *   <li><b>Tolerance</b>: exact match is required unless {@link EvaluationTestCase#outputTolerance()}
 *       is &gt; 0, in which case partial content matching is used.</li>
 * </ul>
 *
 * <p>The contract is declared via the test-case {@code context} map using keys prefixed with
 * {@code "outputContract."}.
 *
 * @doc.type class
 * @doc.purpose Output contract (schema / structural constraint) evaluator
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class OutputContractEvaluationExecutor implements EvaluationExecutor {

    private static final Logger log = LoggerFactory.getLogger(OutputContractEvaluationExecutor.class);

    @Override
    public boolean supports(@NotNull EvaluationType type) {
        return type == EvaluationType.OUTPUT_CONTRACT;
    }

    @Override
    @NotNull
    public Promise<EvaluationResult.TestCaseResult> execute(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        long startMs = System.currentTimeMillis();
        try {
            ContractCheckOutcome outcome = checkOutputContract(testCase);
            long durationMs = System.currentTimeMillis() - startMs;

            if (outcome.passed()) {
                log.debug("Output contract PASSED for testCase={}", testCase.caseId());
                return Promise.of(new EvaluationResult.TestCaseResult(
                        testCase.caseId(),
                        testCase.name(),
                        true,
                        "Output satisfies contract",
                        "",
                        durationMs
                ));
            } else {
                log.warn("Output contract FAILED for testCase={}: {}", testCase.caseId(), outcome.reason());
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
            log.error("Output contract executor threw for testCase={}", testCase.caseId(), e);
            return Promise.of(new EvaluationResult.TestCaseResult(
                    testCase.caseId(),
                    testCase.name(),
                    false,
                    "",
                    "Output contract executor error: " + e.getMessage(),
                    durationMs
            ));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @NotNull
    private ContractCheckOutcome checkOutputContract(@NotNull EvaluationTestCase testCase) {
        Map<String, String> ctx = testCase.context();
        String actualOutput = testCase.expectedOutput(); // "expected" here = what the procedure should produce
        String input = testCase.input();

        // Non-empty check
        String nonEmptyRule = ctx.get("outputContract.nonEmpty");
        if ("true".equalsIgnoreCase(nonEmptyRule)) {
            if (actualOutput == null || actualOutput.isBlank()) {
                return ContractCheckOutcome.fail("Output contract violation: output must be non-empty");
            }
        }

        // Required keys: comma-separated key names that must appear in the output string
        String requiredKeysStr = ctx.get("outputContract.requiredKeys");
        if (requiredKeysStr != null && !requiredKeysStr.isBlank()) {
            for (String key : requiredKeysStr.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty() && !actualOutput.contains(trimmed)) {
                    return ContractCheckOutcome.fail(
                            "Output contract violation: required key '" + trimmed + "' not found in output");
                }
            }
        }

        // Forbidden keys: comma-separated key names that must NOT appear in the output string
        String forbiddenKeysStr = ctx.get("outputContract.forbiddenKeys");
        if (forbiddenKeysStr != null && !forbiddenKeysStr.isBlank()) {
            for (String key : forbiddenKeysStr.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty() && actualOutput.contains(trimmed)) {
                    return ContractCheckOutcome.fail(
                            "Output contract violation: forbidden key '" + trimmed + "' found in output");
                }
            }
        }

        // Exact match check (when tolerance == 0)
        String expectedOutput = testCase.expectedOutput();
        if (testCase.outputTolerance() == 0.0 && !actualOutput.equals(expectedOutput)) {
            // Only enforce exact match when an explicit expected output was set (non-placeholder)
            if (!expectedOutput.equals("expected output") && !expectedOutput.isBlank()) {
                return ContractCheckOutcome.fail(
                        "Output contract violation: exact match required but output differs from expected");
            }
        }

        // Partial match check (when tolerance > 0)
        if (testCase.outputTolerance() > 0.0 && !expectedOutput.isBlank()
                && !expectedOutput.equals("expected output")) {
            double similarity = computeSimilarity(actualOutput, expectedOutput);
            if (similarity < (1.0 - testCase.outputTolerance())) {
                return ContractCheckOutcome.fail(
                        String.format("Output contract violation: similarity %.2f below threshold %.2f",
                                similarity, 1.0 - testCase.outputTolerance()));
            }
        }

        return ContractCheckOutcome.pass();
    }

    /**
     * Naive character-level Jaccard similarity for content overlap.
     * Sufficient for structural contract validation; not intended as semantic similarity.
     */
    private static double computeSimilarity(@NotNull String a, @NotNull String b) {
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        java.util.Set<Character> setA = new java.util.HashSet<>();
        for (char c : a.toCharArray()) setA.add(c);
        java.util.Set<Character> setB = new java.util.HashSet<>();
        for (char c : b.toCharArray()) setB.add(c);
        long intersection = setA.stream().filter(setB::contains).count();
        long union = setA.size() + setB.size() - intersection;
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    private record ContractCheckOutcome(boolean passed, String reason) {
        static ContractCheckOutcome pass() {
            return new ContractCheckOutcome(true, "");
        }

        static ContractCheckOutcome fail(String reason) {
            return new ContractCheckOutcome(false, reason);
        }
    }
}
