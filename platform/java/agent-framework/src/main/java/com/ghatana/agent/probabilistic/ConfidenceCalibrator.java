/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.probabilistic;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Confidence calibration adjusts raw model scores into well-calibrated probabilities.
 *
 * <p>Supported methods:
 * <ul>
 *   <li><b>Isotonic regression</b> — non-parametric, piecewise-linear mapping</li>
 *   <li><b>Platt scaling</b> — logistic regression on model scores</li>
 *   <li><b>Temperature scaling</b> — single parameter scaling</li>
 *   <li><b>Identity</b> — no calibration (pass-through)</li>
 * </ul>
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Calibrates and normalizes confidence scores from model predictions
 * @doc.layer platform
 * @doc.pattern Service
 */
@Value
@Builder(toBuilder = true)
public class ConfidenceCalibrator {

    public enum Method { IDENTITY, ISOTONIC, PLATT, TEMPERATURE }

    @Builder.Default @NotNull Method method = Method.IDENTITY;

    /** Temperature parameter for TEMPERATURE scaling. */
    @Builder.Default double temperature = 1.0;

    /** Platt scaling slope (A parameter). */
    @Builder.Default double plattA = -1.0;

    /** Platt scaling intercept (B parameter). */
    @Builder.Default double plattB = 0.0;

    /** Isotonic calibration breakpoints (x-values). */
    @Nullable double[] isotonicBreakpoints;

    /** Isotonic calibration mapped values (y-values, same length as breakpoints). */
    @Nullable double[] isotonicValues;

    /**
     * Calibrates a raw confidence score.
     *
     * @param rawConfidence score in [0.0, 1.0]
     * @return calibrated confidence in [0.0, 1.0], clamped
     */
    public double calibrate(double rawConfidence) {
        double result = switch (method) {
            case IDENTITY -> rawConfidence;
            case TEMPERATURE -> rawConfidence / temperature;
            case PLATT -> 1.0 / (1.0 + Math.exp(plattA * rawConfidence + plattB));
            case ISOTONIC -> calibrateIsotonic(rawConfidence);
        };
        return Math.max(0.0, Math.min(1.0, result));
    }

    private double calibrateIsotonic(double raw) {
        if (isotonicBreakpoints == null || isotonicValues == null
                || isotonicBreakpoints.length == 0) {
            return raw; // Fallback to identity if not configured
        }

        if (raw <= isotonicBreakpoints[0]) return isotonicValues[0];
        if (raw >= isotonicBreakpoints[isotonicBreakpoints.length - 1]) {
            return isotonicValues[isotonicValues.length - 1];
        }

        // Piecewise-linear interpolation
        for (int i = 1; i < isotonicBreakpoints.length; i++) {
            if (raw <= isotonicBreakpoints[i]) {
                double x0 = isotonicBreakpoints[i - 1], x1 = isotonicBreakpoints[i];
                double y0 = isotonicValues[i - 1], y1 = isotonicValues[i];
                double t = (raw - x0) / (x1 - x0);
                return y0 + t * (y1 - y0);
            }
        }
        return raw;
    }
}
