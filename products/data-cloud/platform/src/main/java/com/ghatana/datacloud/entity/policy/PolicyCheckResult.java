package com.ghatana.datacloud.entity.policy;

import java.util.Objects;

/**
 * Result of a content policy check operation.
 
 *
 * @doc.type record
 * @doc.purpose Policy check result
 * @doc.layer platform
 * @doc.pattern ValueObject
*/
public record PolicyCheckResult(
        boolean passed,
        PolicyType policyType,
        double confidenceScore,
        String violationDetails) {
    public PolicyCheckResult {
        Objects.requireNonNull(policyType, "policyType cannot be null");
        if (confidenceScore < 0.0 || confidenceScore > 1.0) {
            throw new IllegalArgumentException("confidenceScore must be between 0.0 and 1.0");
        }
    }

    public static PolicyCheckResult pass(PolicyType policyType, double confidenceScore) {
        return new PolicyCheckResult(true, policyType, confidenceScore, null);
    }

    public static PolicyCheckResult fail(PolicyType policyType, double confidenceScore, String violationDetails) {
        return new PolicyCheckResult(false, policyType, confidenceScore, violationDetails);
    }

    public PolicyType getPolicyType() {
        return policyType;
    }

    public String getViolationDetails() {
        return violationDetails;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }
}
