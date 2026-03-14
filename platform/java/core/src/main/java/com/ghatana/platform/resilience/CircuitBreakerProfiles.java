/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import java.time.Duration;

/**
 * Pre-defined circuit breaker configuration profiles for K-18 resilience patterns.
 *
 * <p>Three named profiles cover the range of financial and non-financial workloads:
 * <ul>
 *   <li><b>STRICT</b> — For financial operations (order placement, settlement, ledger).
 *       Low failure tolerance; long recovery timeout.</li>
 *   <li><b>STANDARD</b> — Default profile for inter-service calls. Balanced settings.</li>
 *   <li><b>RELAXED</b> — For non-critical paths (reports, dashboards, optional enrichment).
 *       High tolerance; fast recovery.</li>
 * </ul>
 *
 * <p>All profiles can be overridden via K-02 config at runtime using the builder
 * returned by {@link #toBuilder()}.
 *
 * <p>Usage:
 * <pre>{@code
 * CircuitBreaker cb = CircuitBreakerProfiles.strict("ledger-service");
 * // or
 * CircuitBreaker cb = CircuitBreakerProfiles.standard("market-data")
 *         .failureThreshold(8)   // override individual setting
 *         .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Pre-defined circuit breaker profiles for K-18 resilience patterns
 * @doc.layer platform
 * @doc.pattern Factory
 *
 * @since 2.0.0
 */
public final class CircuitBreakerProfiles {

    /**
     * Profile name constant for STRICT — use for financial operations.
     * Failure threshold 3; reset timeout 60s; max backoff 10min.
     */
    public static final String STRICT = "STRICT";

    /**
     * Profile name constant for STANDARD — default inter-service profile.
     * Failure threshold 5; reset timeout 30s; max backoff 5min.
     */
    public static final String STANDARD = "STANDARD";

    /**
     * Profile name constant for RELAXED — for non-critical enrichment paths.
     * Failure threshold 10; reset timeout 15s; max backoff 2min.
     */
    public static final String RELAXED = "RELAXED";

    private CircuitBreakerProfiles() {}

    /**
     * Creates a STRICT circuit breaker for the given service name.
     *
     * <p>STRICT settings: failureThreshold=3, successThreshold=2,
     * resetTimeout=60s, maxBackoff=10min. Intended for financial operations.
     *
     * @param name circuit breaker name (service or endpoint identifier)
     * @return configured CircuitBreaker
     */
    public static CircuitBreaker strict(String name) {
        return strictBuilder(name).build();
    }

    /**
     * Returns a pre-configured builder for STRICT profile (allows overrides).
     *
     * @param name circuit breaker name
     * @return CircuitBreaker.Builder pre-configured with STRICT settings
     */
    public static CircuitBreaker.Builder strictBuilder(String name) {
        return CircuitBreaker.builder(name)
                .failureThreshold(3)
                .successThreshold(2)
                .resetTimeout(Duration.ofSeconds(60))
                .maxBackoff(Duration.ofMinutes(10));
    }

    /**
     * Creates a STANDARD circuit breaker for the given service name.
     *
     * <p>STANDARD settings: failureThreshold=5, successThreshold=2,
     * resetTimeout=30s, maxBackoff=5min. Default for most inter-service calls.
     *
     * @param name circuit breaker name
     * @return configured CircuitBreaker
     */
    public static CircuitBreaker standard(String name) {
        return standardBuilder(name).build();
    }

    /**
     * Returns a pre-configured builder for STANDARD profile (allows overrides).
     *
     * @param name circuit breaker name
     * @return CircuitBreaker.Builder pre-configured with STANDARD settings
     */
    public static CircuitBreaker.Builder standardBuilder(String name) {
        return CircuitBreaker.builder(name)
                .failureThreshold(5)
                .successThreshold(2)
                .resetTimeout(Duration.ofSeconds(30))
                .maxBackoff(Duration.ofMinutes(5));
    }

    /**
     * Creates a RELAXED circuit breaker for the given service name.
     *
     * <p>RELAXED settings: failureThreshold=10, successThreshold=1,
     * resetTimeout=15s, maxBackoff=2min. For non-critical or optional calls.
     *
     * @param name circuit breaker name
     * @return configured CircuitBreaker
     */
    public static CircuitBreaker relaxed(String name) {
        return relaxedBuilder(name).build();
    }

    /**
     * Returns a pre-configured builder for RELAXED profile (allows overrides).
     *
     * @param name circuit breaker name
     * @return CircuitBreaker.Builder pre-configured with RELAXED settings
     */
    public static CircuitBreaker.Builder relaxedBuilder(String name) {
        return CircuitBreaker.builder(name)
                .failureThreshold(10)
                .successThreshold(1)
                .resetTimeout(Duration.ofSeconds(15))
                .maxBackoff(Duration.ofMinutes(2));
    }

    /**
     * Creates a circuit breaker for the named profile string.
     *
     * <p>Useful when the profile is loaded from K-02 config as a string value.
     * Unrecognised profiles fall back to STANDARD.
     *
     * @param name    circuit breaker name
     * @param profile one of {@link #STRICT}, {@link #STANDARD}, {@link #RELAXED}
     * @return configured CircuitBreaker
     */
    public static CircuitBreaker forProfile(String name, String profile) {
        return switch (profile == null ? STANDARD : profile.toUpperCase()) {
            case STRICT -> strict(name);
            case RELAXED -> relaxed(name);
            default -> standard(name);
        };
    }
}
