/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Multi-dimensional confidence vector for mastery assessment.
 *
 * <p>Represents confidence across multiple dimensions rather than a single score.
 * This allows for nuanced decision-making about when to promote skills.
 *
 * @doc.type record
 * @doc.purpose Multi-dimensional confidence vector for mastery assessment
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record ConfidenceVector(
        double correctness,
        double freshness,
        double applicability,
        double safety,
        double transferability,
        double evidenceStrength,
        double regressionStability
) {
    public ConfidenceVector {
        if (correctness < 0.0 || correctness > 1.0) {
            throw new IllegalArgumentException("correctness must be between 0.0 and 1.0");
        }
        if (freshness < 0.0 || freshness > 1.0) {
            throw new IllegalArgumentException("freshness must be between 0.0 and 1.0");
        }
        if (applicability < 0.0 || applicability > 1.0) {
            throw new IllegalArgumentException("applicability must be between 0.0 and 1.0");
        }
        if (safety < 0.0 || safety > 1.0) {
            throw new IllegalArgumentException("safety must be between 0.0 and 1.0");
        }
        if (transferability < 0.0 || transferability > 1.0) {
            throw new IllegalArgumentException("transferability must be between 0.0 and 1.0");
        }
        if (evidenceStrength < 0.0 || evidenceStrength > 1.0) {
            throw new IllegalArgumentException("evidenceStrength must be between 0.0 and 1.0");
        }
        if (regressionStability < 0.0 || regressionStability > 1.0) {
            throw new IllegalArgumentException("regressionStability must be between 0.0 and 1.0");
        }
    }

    /**
     * Creates a confidence vector with all zeros.
     *
     * @return zero confidence vector
     */
    @NotNull
    public static ConfidenceVector zero() {
        return new ConfidenceVector(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Computes the overall execution score as a weighted average.
     * Prioritizes correctness, safety, and freshness.
     *
     * @return execution score between 0.0 and 1.0
     */
    public double executionScore() {
        return (correctness * 0.35) + (safety * 0.30) + (freshness * 0.20) + (applicability * 0.15);
    }

    /**
     * Returns true if this confidence vector indicates mastery eligibility.
     * Requires high correctness, safety, and regression stability.
     *
     * @return true if mastery eligible
     */
    public boolean isMasteryEligible() {
        return correctness >= 0.9
                && safety >= 0.95
                && regressionStability >= 0.9
                && evidenceStrength >= 0.8;
    }

    /**
     * Returns true if this confidence vector requires verification before promotion.
     * Low regression stability or evidence strength triggers verification.
     *
     * @return true if verification required
     */
    public boolean requiresVerification() {
        return regressionStability < 0.8 || evidenceStrength < 0.7;
    }
}
