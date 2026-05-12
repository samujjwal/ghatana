/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

import org.jetbrains.annotations.NotNull;

/**
 * Multi-dimensional score representing mastery quality and reliability.
 *
 * <p>The mastery score combines multiple dimensions into a single execution score:
 * <ul>
 *   <li><b>correctness</b>: How often the skill produces correct results</li>
 *   <li><b>freshness</b>: How recent the skill's knowledge is</li>
 *   <li><b>applicability</b>: How well the skill matches the current context</li>
 *   <li><b>safety</b>: How safe the skill is to execute</li>
 *   <li><b>transferability</b>: How well the skill transfers to similar contexts</li>
 *   <li><b>evidenceStrength</b>: How strong the evidence supporting the skill is</li>
 *   <li><b>regressionStability</b>: How stable the skill is across regression tests</li>
 * </ul>
 *
 * @doc.type record
 * @doc.purpose Multi-dimensional mastery score
 * @doc.layer agent-core
 * @doc.pattern Record
 */
public record MasteryScore(
        double correctness,
        double freshness,
        double applicability,
        double safety,
        double transferability,
        double evidenceStrength,
        double regressionStability
) {
    public MasteryScore {
        if (correctness < 0.0 || correctness > 1.0) {
            throw new IllegalArgumentException("correctness must be in [0.0, 1.0], got: " + correctness);
        }
        if (freshness < 0.0 || freshness > 1.0) {
            throw new IllegalArgumentException("freshness must be in [0.0, 1.0], got: " + freshness);
        }
        if (applicability < 0.0 || applicability > 1.0) {
            throw new IllegalArgumentException("applicability must be in [0.0, 1.0], got: " + applicability);
        }
        if (safety < 0.0 || safety > 1.0) {
            throw new IllegalArgumentException("safety must be in [0.0, 1.0], got: " + safety);
        }
        if (transferability < 0.0 || transferability > 1.0) {
            throw new IllegalArgumentException("transferability must be in [0.0, 1.0], got: " + transferability);
        }
        if (evidenceStrength < 0.0 || evidenceStrength > 1.0) {
            throw new IllegalArgumentException("evidenceStrength must be in [0.0, 1.0], got: " + evidenceStrength);
        }
        if (regressionStability < 0.0 || regressionStability > 1.0) {
            throw new IllegalArgumentException("regressionStability must be in [0.0, 1.0], got: " + regressionStability);
        }
    }

    /**
     * Computes the overall execution score as a product of key dimensions.
     * The execution score combines correctness, freshness, applicability, safety, and regression stability.
     *
     * @return execution score in [0.0, 1.0]
     */
    public double executionScore() {
        return correctness * freshness * applicability * safety * regressionStability;
    }

    /**
     * Creates a zero score (no evidence of mastery).
     *
     * @return zero mastery score
     */
    @NotNull
    public static MasteryScore zero() {
        return new MasteryScore(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Creates a perfect score (ideal mastery).
     *
     * @return perfect mastery score
     */
    @NotNull
    public static MasteryScore perfect() {
        return new MasteryScore(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
    }

    /**
     * Creates a score with only correctness set (for simple scenarios).
     *
     * @param correctness correctness score
     * @return mastery score with only correctness
     */
    @NotNull
    public static MasteryScore correctnessOnly(double correctness) {
        return new MasteryScore(correctness, 0.5, 0.5, 1.0, 0.5, 0.5, 1.0);
    }
}
