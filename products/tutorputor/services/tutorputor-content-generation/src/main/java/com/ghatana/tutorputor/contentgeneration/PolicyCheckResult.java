package com.ghatana.tutorputor.contentgeneration;

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
    PolicyType policyType,
    double score
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
        violations = List.copyOf(violations); // Defensive copy for immutability
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be between 0.0 and 1.0");
        }
    }

    public PolicyCheckResult(boolean passed, List<PolicyViolation> violations, PolicyType policyType) {
        this(passed, violations, policyType, passed ? 1.0 : 0.0);
    }

    /**
     * Creates a passing result with no violations.
     *
     * @param policyType policy that was checked
     * @return PolicyCheckResult indicating pass
     */
    public static PolicyCheckResult pass(PolicyType policyType) {
        return new PolicyCheckResult(true, Collections.emptyList(), policyType, 1.0);
    }

    public static PolicyCheckResult pass(PolicyType policyType, double score) {
        return new PolicyCheckResult(true, Collections.emptyList(), policyType, score);
    }

    /**
     * Creates a failing result with violations.
     *
     * @param policyType policy that was checked
     * @param violations list of violations detected
     * @return PolicyCheckResult indicating failure
     */
    public static PolicyCheckResult fail(PolicyType policyType, List<PolicyViolation> violations) {
        return new PolicyCheckResult(false, violations, policyType, 0.0);
    }

    public static PolicyCheckResult failWithViolations(
            PolicyType policyType,
            List<PolicyViolation> violations,
            double score
    ) {
        return new PolicyCheckResult(false, violations, policyType, score);
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

    /**
     * Represents a single policy violation detected in content.
     *
        * @param policyType policy category that was violated
        * @param severity severity level for the violation
        * @param location content location where the violation was found
        * @param description human-readable explanation of the violation
        * @param remediation recommended remediation for the violation
     *
     * @doc.type record
     * @doc.purpose Policy violation detail
     * @doc.layer domain
     * @doc.pattern Value Object
     */
    public record PolicyViolation(
            PolicyType policyType,
            String severity,
            String location,
            String description,
            String remediation
    ) {
        public PolicyViolation {
            Objects.requireNonNull(policyType, "policyType cannot be null");
            Objects.requireNonNull(severity, "severity cannot be null");
            Objects.requireNonNull(location, "location cannot be null");
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(remediation, "remediation cannot be null");
        }
    }
}
