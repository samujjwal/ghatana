package com.ghatana.products.collection.domain.policy;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of a content policy check operation.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates policy validation outcome including pass/fail status,
 * list of violations detected, and confidence score.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PolicyCheckResult result = new PolicyCheckResult(
 *     false,  // did not pass
 *     List.of(
 *         new PolicyViolation("profanity", "badword", 0.95, 15),
 *         new PolicyViolation("profanity", "anotherbad", 0.88, 42)
 *     ),
 *     PolicyType.PROFANITY
 * );
 * 
 * if (!result.passed()) {
 *     // Handle violations
 *     result.getViolations().forEach(v -> 
 *         log.warn("Violation: {} at position {}", v.matchedText(), v.position()));
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe by design.
 *
 * @doc.type record
 * @doc.purpose Policy check result value object
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record PolicyCheckResult(
        boolean passed,
        List<PolicyViolation> violations,
        PolicyType policyType
) {
    /**
     * Creates policy check result with validation.
     *
     * @param passed whether content passed policy check
     * @param violations list of violations (empty if passed)
     * @param policyType policy that was checked
     * @throws NullPointerException if violations or policyType is null
     */
    public PolicyCheckResult {
        Objects.requireNonNull(violations, "violations cannot be null");
        Objects.requireNonNull(policyType, "policyType cannot be null");
        violations = List.copyOf(violations); // Defensive copy for immutability
    }

    /**
     * Creates a passing result with no violations.
     *
     * @param policyType policy that was checked
     * @return PolicyCheckResult indicating pass
     */
    public static PolicyCheckResult pass(PolicyType policyType) {
        return new PolicyCheckResult(true, Collections.emptyList(), policyType);
    }

    /**
     * Creates a failing result with violations.
     *
     * @param policyType policy that was checked
     * @param violations list of violations detected
     * @return PolicyCheckResult indicating failure
     */
    public static PolicyCheckResult fail(PolicyType policyType, List<PolicyViolation> violations) {
        return new PolicyCheckResult(false, violations, policyType);
    }

    /**
     * Gets count of violations.
     *
     * @return number of violations (0 if passed)
     */
    public int violationCount() {
        return violations.size();
    }

    /**
     * Checks if any violations were detected.
     *
     * @return true if violations exist, false otherwise
     */
    public boolean hasViolations() {
        return !violations.isEmpty();
    }
}

/**
 * Represents a single policy violation detected in content.
 *
 * <p><b>Purpose</b><br>
 * Details about a specific policy violation including the matched text,
 * confidence score, and position in the original content.
 *
 * @param violationType type of violation (e.g., "profanity", "spam_link")
 * @param matchedText exact text that triggered violation
 * @param confidence confidence score (0-1, higher = more certain)
 * @param position character position in original text (0-based)
 *
 * @doc.type record
 * @doc.purpose Policy violation detail
 * @doc.layer domain
 * @doc.pattern Value Object
 */
record PolicyViolation(
        String violationType,
        String matchedText,
        double confidence,
        int position
) {
    public PolicyViolation {
        Objects.requireNonNull(violationType, "violationType cannot be null");
        Objects.requireNonNull(matchedText, "matchedText cannot be null");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        if (position < 0) {
            throw new IllegalArgumentException("position cannot be negative");
        }
    }
}
