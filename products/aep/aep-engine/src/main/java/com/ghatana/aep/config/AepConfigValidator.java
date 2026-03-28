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
        validateCustomConfig(config.customConfig(), violations);
        validateIdempotencySettings(config.customConfig(), violations);
        validateConsentConfig(config.customConfig(), violations);

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

    /**
     * Validates the {@code customConfig} map: keys must be non-blank strings, and
     * values must not be {@code null} (use an empty map to indicate "no custom config").
     *
     * @param customConfig the map to validate (may be empty but must not contain
     *                     null keys or null values)
     * @param violations   the list to append violations to
     */
    private static void validateCustomConfig(java.util.Map<String, Object> customConfig,
                                             List<String> violations) {
        if (customConfig == null) {
            // compact constructor ensures this is never null by the time we get here,
            // but guard defensively in case the validator is called outside that path
            violations.add("customConfig must not be null (use Map.of() for no custom config)");
            return;
        }
        for (java.util.Map.Entry<String, Object> entry : customConfig.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                violations.add("customConfig must not contain null or blank keys");
            }
            if (entry.getValue() == null) {
                violations.add("customConfig key '" + entry.getKey() + "' must not map to null; "
                    + "remove the key or supply a non-null value");
            }
        }
    }

    /**
     * Validates idempotency settings when they are explicitly supplied in {@code customConfig}.
     *
     * <ul>
     *   <li>{@code idempotencyTtlSeconds} — must be a positive integer</li>
     *   <li>{@code idempotencyMaxKeysPerTenant} — must be a positive integer</li>
     * </ul>
     */
    private static void validateIdempotencySettings(java.util.Map<String, Object> customConfig,
                                                    List<String> violations) {
        Object ttl = customConfig.get(Aep.AepConfig.IDEMPOTENCY_TTL_SECONDS_KEY);
        if (ttl != null) {
            if (!(ttl instanceof Number) || ((Number) ttl).intValue() <= 0) {
                violations.add(Aep.AepConfig.IDEMPOTENCY_TTL_SECONDS_KEY
                    + " must be a positive integer, but was: " + ttl);
            }
        }
        Object maxKeys = customConfig.get(Aep.AepConfig.IDEMPOTENCY_MAX_KEYS_PER_TENANT_KEY);
        if (maxKeys != null) {
            if (!(maxKeys instanceof Number) || ((Number) maxKeys).intValue() <= 0) {
                violations.add(Aep.AepConfig.IDEMPOTENCY_MAX_KEYS_PER_TENANT_KEY
                    + " must be a positive integer, but was: " + maxKeys);
            }
        }
    }

    /**
     * Validates consent-related settings when they are explicitly supplied in {@code customConfig}.
     *
     * <ul>
     *   <li>{@code consentProvider} — must be a non-blank string when set</li>
     * </ul>
     */
    private static void validateConsentConfig(java.util.Map<String, Object> customConfig,
                                              List<String> violations) {
        Object provider = customConfig.get(Aep.AepConfig.CONSENT_PROVIDER_KEY);
        if (provider != null) {
            if (!(provider instanceof String) || ((String) provider).isBlank()) {
                violations.add(Aep.AepConfig.CONSENT_PROVIDER_KEY
                    + " must be a non-blank string when set, but was: " + provider);
            }
        }
    }
}
