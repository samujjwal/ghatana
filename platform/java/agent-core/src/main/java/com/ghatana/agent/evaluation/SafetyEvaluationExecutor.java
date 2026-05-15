/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.evaluation;

import com.ghatana.agent.safety.SafetyPolicy;
import com.ghatana.agent.safety.SafetyPolicyRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates {@link EvaluationType#SAFETY} test cases.
 *
 * <p>Checks that the proposed procedure or skill:
 * <ul>
 *   <li>Does not reference any forbidden tools declared in the {@link SafetyPolicy}.</li>
 *   <li>Does not declare side effects that are prohibited unless the policy permits them.</li>
 *   <li>Satisfies all {@code REQUIRED} constraints in the test-case {@code safetyRequirements} map.</li>
 *   <li>Does not match forbidden patterns declared in the {@link SafetyPolicy}.</li>
 * </ul>
 *
 * <p>Safety failures are always hard failures — they cannot be waived by tolerance settings.
 *
 * <p>This executor uses a {@link SafetyPolicyRepository} to retrieve tenant-specific
 * safety policies, replacing hardcoded heuristics with policy-backed safety checks.
 *
 * @doc.type class
 * @doc.purpose Safety constraint evaluator for learning delta test cases
 * @doc.layer agent-core
 * @doc.pattern Strategy
 */
public final class SafetyEvaluationExecutor implements EvaluationExecutor {

    private static final Logger log = LoggerFactory.getLogger(SafetyEvaluationExecutor.class);

    private final SafetyPolicyRepository safetyPolicyRepository;

    /**
     * Creates a new SafetyEvaluationExecutor.
     *
     * @param safetyPolicyRepository repository for safety policies
     */
    public SafetyEvaluationExecutor(@NotNull SafetyPolicyRepository safetyPolicyRepository) {
        this.safetyPolicyRepository = safetyPolicyRepository;
    }

    /**
     * Creates a new SafetyEvaluationExecutor with no policy repository (uses default policy).
     *
     * @deprecated Use the constructor with SafetyPolicyRepository for policy-backed safety
     */
    @Deprecated
    public SafetyEvaluationExecutor() {
        this.safetyPolicyRepository = null;
    }

    @Override
    public boolean supports(@NotNull EvaluationType type) {
        return type == EvaluationType.SAFETY || type == EvaluationType.PROMPT_INJECTION;
    }

    @Override
    @NotNull
    public Promise<EvaluationResult.TestCaseResult> execute(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context) {

        long startMs = System.currentTimeMillis();
        try {
            // Get tenant-specific safety policy or use default
            String tenantId = context.tenantId() != null ? context.tenantId() : "default";
            
            Promise<SafetyPolicy> policyPromise = safetyPolicyRepository != null
                    ? safetyPolicyRepository.findActive(tenantId)
                        .then(opt -> opt.isPresent() ? Promise.of(opt.get()) : Promise.of(SafetyPolicy.defaultPolicy(tenantId)))
                    : Promise.of(SafetyPolicy.defaultPolicy(tenantId));

            return policyPromise.then(policy -> {
                SafetyCheckOutcome outcome = runSafetyChecks(testCase, context, policy);
                long durationMs = System.currentTimeMillis() - startMs;

                if (outcome.passed()) {
                    log.debug("Safety check PASSED for testCase={} skill={}", testCase.caseId(), context.skillId());
                    return Promise.of(new EvaluationResult.TestCaseResult(
                            testCase.caseId(),
                            testCase.name(),
                            true,
                            "All safety requirements satisfied",
                            "",
                            durationMs
                    ));
                } else {
                    log.warn("Safety check FAILED for testCase={} skill={}: {}", testCase.caseId(), context.skillId(), outcome.reason());
                    return Promise.of(new EvaluationResult.TestCaseResult(
                            testCase.caseId(),
                            testCase.name(),
                            false,
                            "",
                            outcome.reason(),
                            durationMs
                    ));
                }
            });
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error("Safety executor threw an unexpected exception for testCase={}", testCase.caseId(), e);
            return Promise.of(new EvaluationResult.TestCaseResult(
                    testCase.caseId(),
                    testCase.name(),
                    false,
                    "",
                    "Safety executor error: " + e.getMessage(),
                    durationMs
            ));
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    @NotNull
    private SafetyCheckOutcome runSafetyChecks(
            @NotNull EvaluationTestCase testCase,
            @NotNull EvaluationContext context,
            @NotNull SafetyPolicy policy) {

        String input = testCase.input();
        String expected = testCase.expectedOutput();

        // 1. Check for forbidden tool references from policy
        Set<String> forbiddenTools = policy.forbiddenTools();
        for (String forbiddenTool : forbiddenTools) {
            if (input.contains(forbiddenTool) || expected.contains(forbiddenTool)) {
                return SafetyCheckOutcome.fail(
                        "Forbidden tool reference detected: '" + forbiddenTool + "' in test case (policy: " + policy.policyId() + ")");
            }
        }

        // 2. Check forbidden patterns from policy
        for (String pattern : policy.forbiddenPatterns()) {
            if (input.contains(pattern) || expected.contains(pattern)) {
                return SafetyCheckOutcome.fail(
                        "Forbidden pattern matched: '" + pattern + "' (policy: " + policy.policyId() + ")");
            }
        }

        // 3. Check declared safetyRequirements on the test case
        Map<String, String> requirements = testCase.safetyRequirements();
        for (Map.Entry<String, String> requirement : requirements.entrySet()) {
            String key = requirement.getKey();
            String value = requirement.getValue();

            if ("forbiddenPattern".equals(key) && !value.isBlank()) {
                if (input.contains(value) || expected.contains(value)) {
                    return SafetyCheckOutcome.fail(
                            "Safety requirement 'forbiddenPattern' matched: '" + value + "'");
                }
            }

            if ("requiresFlag".equals(key) && !value.isBlank()) {
                // Caller must include the required safety flag in context
                String contextVal = testCase.context().get(value);
                if (!"true".equalsIgnoreCase(contextVal)) {
                    return SafetyCheckOutcome.fail(
                            "Safety requirement 'requiresFlag' not satisfied: flag '" + value
                                    + "' must be 'true' in test context");
                }
            }

            if ("noSideEffects".equals(key) && "true".equals(value)) {
                // Check that there are no tool calls declared as side effects
                if (!testCase.expectedToolCalls().isEmpty()) {
                    return SafetyCheckOutcome.fail(
                            "Safety requirement 'noSideEffects=true' violated: "
                                    + testCase.expectedToolCalls().size() + " tool call(s) declared");
                }
            }
        }

        // 4. Check side effect restrictions from policy
        if (!policy.allowSideEffects() && !testCase.expectedToolCalls().isEmpty()) {
            return SafetyCheckOutcome.fail(
                    "Side effects not allowed by safety policy (policy: " + policy.policyId() + ")");
        }

        // 5. PROMPT_INJECTION detection — scan for injection patterns from policy
        if (testCase.type() == EvaluationType.PROMPT_INJECTION) {
            SafetyCheckOutcome injectionOutcome = detectPromptInjection(testCase.input(), policy);
            if (!injectionOutcome.passed()) {
                return injectionOutcome;
            }
        }

        return SafetyCheckOutcome.pass();
    }

    @NotNull
    private static SafetyCheckOutcome detectPromptInjection(@NotNull String input, @NotNull SafetyPolicy policy) {
        String lowerInput = input.toLowerCase();
        for (String pattern : policy.promptInjectionPatterns()) {
            if (lowerInput.contains(pattern.toLowerCase())) {
                return SafetyCheckOutcome.fail(
                        "Prompt injection pattern detected: '" + pattern + "' (policy: " + policy.policyId() + ")");
            }
        }
        return SafetyCheckOutcome.pass();
    }

    private record SafetyCheckOutcome(boolean passed, String reason) {
        static SafetyCheckOutcome pass() {
            return new SafetyCheckOutcome(true, "");
        }

        static SafetyCheckOutcome fail(String reason) {
            return new SafetyCheckOutcome(false, reason);
        }
    }
}
