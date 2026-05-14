/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.context.version.VersionContext;
import com.ghatana.agent.context.version.VersionContextCodec;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Evaluates {@link EvaluationType#VERSION_COMPATIBILITY} and
 * {@link EvaluationType#COMPATIBILITY} test cases.
 *
 * <p>Verifies that the proposed artifact (procedure, semantic fact, retrieval policy, etc.)
 * is compatible with the runtime versions declared in the {@link EvaluationContext}.
 *
 * <p>Compatibility is checked by:
 * <ol>
 *   <li>Decoding the version context from the evaluation context's metadata using
 *       {@link VersionContextCodec}.</li>
 *   <li>Comparing the declared {@code versionScope} on the test case against the
 *       resolved runtimes/dependencies in the version context.</li>
 *   <li>Checking explicit version constraints in the test-case {@code context} map under
 *       keys prefixed with {@code "compat."}: e.g. {@code "compat.jvm=21.x"},
 *       {@code "compat.springBoot=3.x"}.</li>
 * </ol>
 *
 * <p>When no explicit constraints are declared and no version context is available the
 * test case passes (no evidence of incompatibility).
 *
 * @doc.type class
 * @doc.purpose Version compatibility evaluator for learning delta test cases
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class VersionCompatibilityEvaluationExecutor implements EvaluationExecutor {

    private static final Logger log = LoggerFactory.getLogger(VersionCompatibilityEvaluationExecutor.class);

    @Override
    public boolean supports(@NotNull EvaluationType type) {
        return type == EvaluationType.VERSION_COMPATIBILITY
                || type == EvaluationType.COMPATIBILITY;
    }

    @Override
    @NotNull
    public Promise<EvaluationResult.TestCaseResult> execute(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        long startMs = System.currentTimeMillis();
        try {
            CompatCheckOutcome outcome = checkCompatibility(testCase, context);
            long durationMs = System.currentTimeMillis() - startMs;

            if (outcome.passed()) {
                log.debug("Version compatibility PASSED for testCase={}", testCase.caseId());
                return Promise.of(new EvaluationResult.TestCaseResult(
                        testCase.caseId(),
                        testCase.name(),
                        true,
                        "Version compatibility satisfied",
                        "",
                        durationMs
                ));
            } else {
                log.warn("Version compatibility FAILED for testCase={}: {}", testCase.caseId(), outcome.reason());
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
            log.error("Version compatibility executor threw for testCase={}", testCase.caseId(), e);
            return Promise.of(new EvaluationResult.TestCaseResult(
                    testCase.caseId(),
                    testCase.name(),
                    false,
                    "",
                    "Version compatibility executor error: " + e.getMessage(),
                    durationMs
            ));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @NotNull
    private CompatCheckOutcome checkCompatibility(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        // Decode the version context from the evaluation context metadata
        Map<String, String> metadata = context.metadata();
        String vcJson = metadata != null ? metadata.get("versionContextJson") : null;
        VersionContext versionContext = VersionContextCodec.INSTANCE.decodeOrEmpty(vcJson);

        // Check explicit "compat.*" constraints declared in the test-case context map
        Map<String, String> testCtx = testCase.context();
        for (Map.Entry<String, String> entry : testCtx.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("compat.")) continue;

            String componentName = key.substring("compat.".length());
            String requiredPattern = entry.getValue();

            // Look up the actual version in runtimes first, then dependencies
            String actualVersion = versionContext.runtimes().get(componentName);
            if (actualVersion == null) {
                actualVersion = versionContext.dependencies().get(componentName);
            }
            if (actualVersion == null) {
                actualVersion = versionContext.tools().get(componentName);
            }

            if (actualVersion == null) {
                // Component not declared in version context — treat as unknown (pass)
                log.debug("Component '{}' not in version context; skipping compatibility check", componentName);
                continue;
            }

            if (!versionMatches(actualVersion, requiredPattern)) {
                return CompatCheckOutcome.fail(
                        String.format("Version compatibility violation: component '%s' version '%s' " +
                                        "does not satisfy requirement '%s'",
                                componentName, actualVersion, requiredPattern));
            }
        }

        // Check versionScope declared on the test case
        String declaredScope = testCase.versionScope();
        if (!"latest".equals(declaredScope) && !declaredScope.isBlank()) {
            // Scope is a specific version pattern — validate that it overlaps with context
            String sourceRef = versionContext.sourceRef();
            if (sourceRef != null && !sourceRef.isBlank() && !sourceRef.equals("unknown")) {
                if (!versionContext.sourceRef().contains(declaredScope) &&
                        !declaredScope.equals("*")) {
                    return CompatCheckOutcome.fail(
                            "Version scope '" + declaredScope + "' does not match source ref '" + sourceRef + "'");
                }
            }
        }

        return CompatCheckOutcome.pass();
    }

    /**
     * Matches an actual version string against a pattern.
     * Patterns ending with {@code .x} are treated as prefix patterns;
     * patterns ending with {@code *} match any; all others require exact match.
     */
    private static boolean versionMatches(@NotNull String actual, @NotNull String pattern) {
        if ("*".equals(pattern)) return true;
        if (pattern.endsWith(".x")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return actual.startsWith(prefix);
        }
        return actual.equals(pattern);
    }

    private record CompatCheckOutcome(boolean passed, String reason) {
        static CompatCheckOutcome pass() {
            return new CompatCheckOutcome(true, "");
        }

        static CompatCheckOutcome fail(String reason) {
            return new CompatCheckOutcome(false, reason);
        }
    }
}
