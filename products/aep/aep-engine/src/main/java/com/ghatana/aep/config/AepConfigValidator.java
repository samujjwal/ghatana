/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.config;

import com.ghatana.aep.Aep;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates {@link Aep.AepConfig} instances before engine creation.
 *
 * <p>The {@link Aep.AepConfig} compact constructor performs silent normalizations
 * (e.g. clamping {@code anomalyThreshold} to {@code 0.9} when out of range). While
 * this prevents crashes, it hides misconfiguration bugs in production. This validator
 * applies explicit, fail-fast checks with actionable error messages so that
 * operators know exactly what changed.
 *
 * <p>Typical usage from {@link Aep#create(Aep.AepConfig)}:
 * <pre>{@code
 * AepConfigValidator.validate(config); // throws IllegalArgumentException on failure
 * }</pre>
 *
 * <h2>Validated Constraints</h2>
 * <ul>
 *   <li>{@code anomalyThreshold} — must be strictly between {@code 0.0} and {@code 1.0}
 *       (exclusive). Values at the boundaries are pathological: 0.0 marks everything
 *       as anomalous; 1.0 marks nothing.</li>
 *   <li>{@code maxPipelinesPerTenant} — must be between {@code 1} and {@code 10 000}
 *       (inclusive). Zero allows no pipelines; values above 10 000 indicate a
 *       runaway configuration that would exhaust resources.</li>
 *   <li>{@code workerThreads} — must be positive (≥ 1). Zero or negative values
 *       indicate an unresolved default that slipped past the compact constructor.</li>
 *   <li>{@code instanceId} — must not be blank when explicitly set.
 *       {@code null} is allowed (factory generates a UUID in the compact constructor).</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Fail-fast validation of AepConfig before engine creation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepConfigValidator {

    private static final double ANOMALY_THRESHOLD_MIN = 0.0;
    private static final double ANOMALY_THRESHOLD_MAX = 1.0;
    private static final int MAX_PIPELINES_UPPER_BOUND = 10_000;

    private AepConfigValidator() {
        // Utility class — not instantiable
    }

    /**
     * Validate the supplied configuration and throw {@link IllegalArgumentException}
     * if any constraint is violated.
     *
     * @param config configuration to validate (must not be {@code null})
     * @throws IllegalArgumentException if one or more constraints are violated;
     *                                  the exception message lists all violations
     * @throws NullPointerException     if {@code config} is {@code null}
     */
    public static void validate(Aep.AepConfig config) {
        java.util.Objects.requireNonNull(config, "config must not be null");

        List<String> violations = new ArrayList<>();

        validateAnomalyThreshold(config.anomalyThreshold(), violations);
        validateMaxPipelinesPerTenant(config.maxPipelinesPerTenant(), violations);
        validateWorkerThreads(config.workerThreads(), violations);
        validateInstanceId(config.instanceId(), violations);

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("AepConfig validation failed: " + String.join("; ", violations));
        }
    }

    // ── Individual field validators ─────────────────────────────────────────

    private static void validateAnomalyThreshold(double value, List<String> violations) {
        if (value <= ANOMALY_THRESHOLD_MIN || value >= ANOMALY_THRESHOLD_MAX) {
            violations.add(String.format(
                "anomalyThreshold must be strictly between %.1f and %.1f (exclusive), but was %.4f",
                ANOMALY_THRESHOLD_MIN, ANOMALY_THRESHOLD_MAX, value));
        }
    }

    private static void validateMaxPipelinesPerTenant(int value, List<String> violations) {
        if (value < 1 || value > MAX_PIPELINES_UPPER_BOUND) {
            violations.add(String.format(
                "maxPipelinesPerTenant must be between 1 and %d (inclusive), but was %d",
                MAX_PIPELINES_UPPER_BOUND, value));
        }
    }

    private static void validateWorkerThreads(int value, List<String> violations) {
        if (value < 1) {
            violations.add("workerThreads must be >= 1, but was " + value);
        }
    }

    private static void validateInstanceId(String instanceId, List<String> violations) {
        // null is acceptable (compact constructor generates a UUID); blank string is not
        if (instanceId != null && instanceId.isBlank()) {
            violations.add("instanceId must not be blank when explicitly provided");
        }
    }
}
